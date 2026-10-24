#include <stdio.h>

unsigned long long fib(int n) {
    unsigned long long a = 0;
    unsigned long long b = 1;
    unsigned long long c = 0;
    
    if (n == 0) return a;
    
    for (int i = 2; i <= n; i++) {
        c = a + b;
        a = b;
        b = c;
    }
    return b;
}

int main() {
    int passes = 50000000; 
    unsigned long long last = 0;
    
    for (int i = 0; i < passes; i++) {
        last += fib(i % 90);
    }
    
    return last;
}
