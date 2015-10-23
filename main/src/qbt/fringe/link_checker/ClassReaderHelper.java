package qbt.fringe.link_checker;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public abstract class ClassReaderHelper {
    public void readClass(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        final String className = cr.getClassName();
        final String classNameSlashed = className.replace('.', '/');
        onProvides(classNameSlashed, Member.self());
        onInherits(classNameSlashed, cr.getSuperName());
        for(String ifaceName : cr.getInterfaces()) {
            onInherits(classNameSlashed, ifaceName);
        }
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5) {
            private String ownerFromDesc(String desc) {
                Type descType = Type.getType(desc);
                if(descType.getSort() != Type.OBJECT) {
                    throw new IllegalStateException();
                }
                return descType.getClassName().replace('.', '/');
            }

            private void checkType(Type t) {
                switch(t.getSort()) {
                    case Type.ARRAY:
                        checkType(t.getElementType());
                        break;

                    case Type.OBJECT:
                        onUses(className, t.getClassName().replace('.', '/'), Member.self());
                        break;
                }
            }

            private final AnnotationVisitor av = new AnnotationVisitor(Opcodes.ASM5) {
                @Override
                public void visit(String name, Object value) {
                    if(value instanceof Type) {
                        checkType((Type)value);
                    }
                }

                @Override
                public AnnotationVisitor visitAnnotation(String name, String desc) {
                    onUses(className, ownerFromDesc(desc), Member.self());
                    return this;
                }

                @Override
                public AnnotationVisitor visitArray(String name) {
                    return this;
                }

                @Override
                public void visitEnum(String name, String desc, String value) {
                    onUses(className, ownerFromDesc(desc), Member.field(desc, value));
                }
            };

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                onUses(className, ownerFromDesc(desc), Member.self());
                return av;
            }

            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                onProvides(className, Member.field(desc, name));
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String methodName, String methodDesc, String signature, String[] exceptions) {
                onProvides(className, Member.method((access & Opcodes.ACC_STATIC) != 0, methodDesc, methodName));
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                        onUses(className, owner, Member.field(desc, name));
                    }

                    @Override
                    public void visitLdcInsn(Object cst) {
                        if(cst instanceof Type) {
                            checkType((Type) cst);
                        }
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        onUses(className, owner, Member.method(opcode == Opcodes.INVOKESTATIC, desc, name));
                    }
                };
            }
        };
        cr.accept(cv, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    protected abstract void onProvides(String clazz, Member member);
    protected abstract void onInherits(String clazz, String superClass);
    protected abstract void onUses(String from, String to, Member member);
}
