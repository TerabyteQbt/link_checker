package qbt.fringe.link_checker;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import misc1.commons.options.OptionsFragment;
import misc1.commons.options.OptionsLibrary;
import misc1.commons.options.OptionsResults;
import misc1.commons.options.SimpleMain;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

public class Main extends SimpleMain<Main.Options, Exception> {
    public static interface Options {
        public static final OptionsLibrary<Options> o = OptionsLibrary.of();
        public static final OptionsFragment<Options, ImmutableList<String>> checks = o.oneArg("check").helpDesc("Check this jar/class");
        public static final OptionsFragment<Options, ImmutableList<String>> libs = o.oneArg("lib").helpDesc("Parse this jar/class as a required library but do not check it");
        public static final OptionsFragment<Options, ImmutableList<String>> whitelistFrom = o.oneArg("whitelistFrom").helpDesc("Whitelist calls from this prefix");
        public static final OptionsFragment<Options, ImmutableList<String>> whitelistTo = o.oneArg("whitelistTo").helpDesc("Whitelist calls to this prefix");
        public static final OptionsFragment<Options, Boolean> qbtDefaults = o.zeroArg("qbtDefaults").transform(o.flag()).helpDesc("Configure check and lib from QBT artifacts directories");
        public static final OptionsFragment<Options, ?> help = simpleHelpOption();
    }

    @Override
    protected Class<Options> getOptionsClass() {
        return Options.class;
    }

    private static class Parser {
        private final ImmutableSet.Builder<Pair<String, Member>> providesBuilder = ImmutableSet.builder();
        private final ImmutableMultimap.Builder<String, String> immediateSupersBuilder = ImmutableMultimap.builder();
        private final ImmutableSet.Builder<Triple<String, String, Member>> usesBuilder = ImmutableSet.builder();
        private final Set<String> found = Sets.newHashSet();
        private final Set<String> checked = Sets.newHashSet();
        private final Set<String> missing = Sets.newHashSet();
        private int errors = 0;

        protected final ClassReaderHelper checkClassReader = new ClassReaderHelper() {
            @Override
            protected void onProvides(String clazz, Member member) {
                if(member.equals(Member.self())) {
                    missing.remove(clazz);
                    found.add(clazz);
                    checked.add(clazz);
                }
                providesBuilder.add(Pair.of(clazz, member));
            }

            @Override
            protected void onInherits(String clazz, String superClass) {
                if(superClass == null) {
                    // Object!
                    return;
                }
                if(!found.contains(superClass)) {
                    missing.add(superClass);
                }
                immediateSupersBuilder.put(clazz, superClass);
            }

            @Override
            protected void onUses(String from, String to, Member member) {
                if(!found.contains(to)) {
                    missing.add(to);
                }
                usesBuilder.add(Triple.of(from, to, member));
            }
        };

        private final ClassReaderHelper libClassReader = new ClassReaderHelper() {
            @Override
            protected void onProvides(String clazz, Member member) {
                if(member.equals(Member.self())) {
                    missing.remove(clazz);
                    found.add(clazz);
                }
                providesBuilder.add(Pair.of(clazz, member));
            }

            @Override
            protected void onInherits(String clazz, String superClass) {
                if(superClass == null) {
                    // Object!
                    return;
                }
                if(!found.contains(superClass)) {
                    missing.add(superClass);
                }
                immediateSupersBuilder.put(clazz, superClass);
            }

            @Override
            protected void onUses(String from, String to, Member member) {
            }
        };

        private void common(String arg, ClassReaderHelper crh) throws IOException {
            if(arg.endsWith(".jar")) {
                try(ZipFile zf = new ZipFile(arg)) {
                    Enumeration<? extends ZipEntry> en = zf.entries();
                    while(en.hasMoreElements()) {
                        final ZipEntry ze = en.nextElement();
                        if(ze.getName().endsWith(".class")) {
                            crh.readClass(new ByteSource() {
                                @Override
                                public InputStream openStream() throws IOException {
                                    return zf.getInputStream(ze);
                                }
                            }.read());
                        }
                    }
                }
                return;
            }

            if(arg.endsWith(".class")) {
                crh.readClass(Files.toByteArray(new File(arg)));
                return;
            }

            throw new IllegalArgumentException("Given unintelligible argument: " + arg);
        }

        private void check(String check) throws IOException {
            common(check, checkClassReader);
        }

        private void lib(String lib) throws IOException {
            common(lib, libClassReader);
        }

        private void completeMissing() throws IOException {
            while(true) {
                Iterator<String> i = missing.iterator();
                if(!i.hasNext()) {
                    return;
                }
                String clazz = i.next();
                i.remove();
                found.add(clazz);

                if(clazz.startsWith("[")) {
                    libClassReader.onProvides(clazz, Member.self());
                    libClassReader.onInherits(clazz, "java/lang/Object");
                }
                else {
                    String resource = clazz + ".class";
                    final URL url = ClassLoader.getSystemClassLoader().getResource(resource);
                    if(url == null) {
                        // Well, this sucks.  Unfortunately if this was from
                        // e.g.  a --check jar we actually probably don't want
                        // to shit a brick.  If it matters you'll get errors
                        // from the things you referenced inheritted that we
                        // couldn't find.  It may be slightly less clear in
                        // some places but this doesn't change pass/fail w/o a
                        // whitelist and it makes pass/fail a little saner with
                        // whitelisting.

                        //System.out.println("Couldn't find class " + clazz);
                        //++errors;
                        continue;
                    }
                    libClassReader.readClass(Resources.toByteArray(url));
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new Main().exec(args);
    }

    @Override
    public int run(OptionsResults<Options> o) throws Exception {
        Parser p = new Parser();
        for(String check : o.get(Options.checks)) {
            p.check(check);
        }
        for(String lib : o.get(Options.libs)) {
            p.lib(lib);
        }
        if(o.get(Options.qbtDefaults)) {
            for(File packageDir : new File(System.getenv("INPUT_ARTIFACTS_DIR"), "strong").listFiles()) {
                if(packageDir.getName().startsWith(".")) {
                    continue;
                }
                File jarsDir = new File(packageDir, "jars");
                if(!jarsDir.isDirectory()) {
                    continue;
                }
                for(File jar : jarsDir.listFiles()) {
                    if(jar.isFile() && jar.getName().endsWith(".jar")) {
                        p.lib(jar.getPath());
                    }
                }
            }
            File jarsDir = new File(System.getenv("OUTPUT_ARTIFACTS_DIR"), "jars");
            if(jarsDir.isDirectory()) {
                for(File jar : jarsDir.listFiles()) {
                    if(jar.isFile() && jar.getName().endsWith(".jar")) {
                        p.check(jar.getPath());
                    }
                }
            }
        }
        p.completeMissing();

        List<String> whitelistFrom = o.get(Options.whitelistFrom);
        List<String> whitelistTo = o.get(Options.whitelistTo);
        final ImmutableSet<Pair<String, Member>> provides = p.providesBuilder.build();
        final ImmutableMultimap<String, String> immediateSupers = p.immediateSupersBuilder.build();
        int errors = p.errors;
        for(Triple<String, String, Member> t : p.usesBuilder.build()) {
            String from = t.getLeft();
            String to = t.getMiddle();
            Member member = t.getRight();
            class Finder {
                private final Set<String> started = Sets.newHashSet();

                boolean find(String clazz, Member member) {
                    if(!started.add(clazz)) {
                        return false;
                    }
                    if(provides.contains(Pair.of(clazz, member))) {
                        return true;
                    }
                    for(String superClass : immediateSupers.get(clazz)) {
                        if(find(superClass, member)) {
                            return true;
                        }
                    }
                    return false;
                }
            }
            if(matchesWhitelist(whitelistFrom, from)) {
                continue;
            }
            if(matchesWhitelist(whitelistTo, to)) {
                continue;
            }
            if(!new Finder().find(to, member)) {
                System.out.println(from + " uses " + member + " in " + to + " which is not present!");
                ++errors;
            }
        }
        if(errors > 0) {
            System.out.println("Link checking complete with " + errors + " errors.");
            return 1;
        }
        else {
            System.out.println("Link checking complete without errors.");
            return 0;
        }
    }

    private static boolean matchesWhitelist(List<String> whitelist, String element) {
        for(String whitelisted : whitelist) {
            if(element.startsWith(whitelisted + "/")) {
                return true;
            }
            if(element.equals(whitelisted)) {
                return true;
            }
        }
        return false;
    }
}
