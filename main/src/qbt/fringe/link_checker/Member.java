//   Copyright 2016 Keith Amling
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//
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
