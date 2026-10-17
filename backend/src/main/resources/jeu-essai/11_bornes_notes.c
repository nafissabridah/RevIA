#include <stdio.h>
#define NB_NOTES 5

double moyenne(void) {
    double notes[NB_NOTES] = {12, 15, 9, 18, 14};
    double somme = 0;
    for (int i = 0; i <= NB_NOTES; i++) {
        somme += notes[i];
    }
    return somme / NB_NOTES;
}
