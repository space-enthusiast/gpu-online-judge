#include <stdio.h>
#include <stdlib.h>

int main() {
    int n;
    scanf("%d", &n);
    float *A = malloc(n * n * sizeof(float));
    float *B = malloc(n * n * sizeof(float));
    float *C = calloc(n * n, sizeof(float));
    for (int i = 0; i < n * n; i++) scanf("%f", &A[i]);
    for (int i = 0; i < n * n; i++) scanf("%f", &B[i]);

    for (int i = 0; i < n; i++)
        for (int k = 0; k < n; k++)
            for (int j = 0; j < n; j++)
                C[i * n + j] += A[i * n + k] * B[k * n + j];

    for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
            if (j) printf(" ");
            printf("%.2f", C[i * n + j]);
        }
        printf("\n");
    }
    free(A); free(B); free(C);
    return 0;
}
