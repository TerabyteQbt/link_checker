package qbt.fringe.link_checker;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

public final class Member {
    private final Object identityDelegate;
    private final String toString;

    private Member(Object identityDelegate, String toString) {
        this.identityDelegate = identityDelegate;
        this.toString = toString;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(identityDelegate);
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof Member)) {
            return false;
        }
        Member other = (Member) obj;
        return Objects.equal(identityDelegate, other.identityDelegate);
    }

    @Override
    public String toString() {
        return toString;
    }

    public static Member method(boolean isStatic, String desc, String name) {
        return new Member(ImmutableList.of("method", isStatic, desc, name), "method " + desc + " " + name);
    }

    public static Member field(String desc, String name) {
        return new Member(ImmutableList.of("field", desc, name), "field " + desc + " " + name);
    }

    public static Member self() {
        return new Member(ImmutableList.of("self"), "self");
    }
}
