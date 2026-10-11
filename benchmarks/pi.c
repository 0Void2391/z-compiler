#include <stdio.h>

double compute(int iterations) {
    double pi = 0.0;
    double sign = 1.0;

    for (int i = 0; i < iterations; i++) {
        pi = pi + (sign / (2 * i + 1));
        sign = -sign;
    }

    return pi * 4.0;
}

int main() {
    int passes = 100000000;
    double result = compute(passes);
    return result;
}
