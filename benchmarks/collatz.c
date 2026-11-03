#include <stdint.h>
#include <stdio.h>

static int64_t collatz(uint64_t n) {
    int64_t steps = 0;

    for (; n > 1; steps++) {
        if (n % 2 == 0) {
            n = n / 2;
        } else {
            n = 3 * n + 1;
        }
    }

    return steps;
}

int main(void) {
    const uint64_t limit = 1000000;
    int64_t max = 0;

    for (uint64_t i = 1; i <= limit; i++) {
        const int64_t current = collatz(i);
        if (current > max) {
            max = current;
        }
    }

    printf("%lld\n", (long long)max);
    return 0;
}
