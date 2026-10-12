#include <stdio.h>

int collatz(unsigned long long n) {
    int steps = 0;
    
    for (; n > 1; steps++) {
        if (n % 2 == 0) {
            n = n / 2;
        } else {
            n = 3 * n + 1;
        }
    }
    return steps;
}

int main() {
    int max = 0;
    int limit = 1000000; 
    
    for (int i = 1; i <= limit; i++) {
        int curr = collatz(i);
        if (curr > max) {
            max = curr;
        }
    }
    
    return max;
}
