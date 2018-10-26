/*
 * Copyright 2018 Griffin DeJohn
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.github.gdejohn.procrastination;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumersTest {
    @Test
    void fix() {
        var integer = new Object() {
            int value = 1;
        };
        Consumers.<Integer>fix(
            a -> i -> {
                if (i > 0) {
                    integer.value <<= 1;
                    a.accept(i - 1);
                }
            }
        ).accept(16);
        assertThat(integer.value).isEqualTo(1 << 16);
    }
}
