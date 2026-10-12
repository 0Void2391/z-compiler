#include <stdio.h>

int isPrime(int n) {
    if (n <= 1) return 0;
    
    for (int i = 2; i * i <= n; i++) {
        if (n % i == 0) {
            return 0; 
        }
    }
    return 1; 
}

int main() {
    int limit = 5000000; 
    int counter = 0;
    
    for (int i = 1; i <= limit; i++) {
        if (isPrime(i) == 1) {
            counter = counter + 1;
        }
    }
    
    return counter;
}
