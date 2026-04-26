#include <stdio.h>
#include <stdlib.h>

int main() {
    int n;
    scanf("%d", &n);
    float *a = malloc(n * sizeof(float));
    float *b = malloc(n * sizeof(float));

    for (int i = 0; i < n; i++) scanf("%f", &a[i]);
    for (int i = 0; i < n; i++) scanf("%f", &b[i]);

    for (int i = 0; i < n; i++) {
        if (i) printf(" ");
        printf("%.2f", a[i] + b[i]);
    }
    printf("\n");

    free(a);
    free(b);
    return 0;
}
