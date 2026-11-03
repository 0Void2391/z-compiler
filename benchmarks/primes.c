#include <stdint.h>
#include <stdio.h>

static int64_t is_prime(int64_t n) {
    if (n <= 1) return 0;

    for (int64_t i = 2; i * i <= n; i++) {
        if (n % i == 0) {
            return 0;
        }
    }

    return 1;
}

int main(void) {
    const int64_t limit = 5000000;
    int64_t counter = 0;

    for (int64_t i = 1; i <= limit; i++) {
        if (is_prime(i) == 1) {
            counter = counter + 1;
        }
    }

    printf("%lld\n", (long long)counter);
    return 0;
}
