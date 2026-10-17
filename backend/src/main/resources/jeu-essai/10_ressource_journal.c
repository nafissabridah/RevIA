#include <stdio.h>

int ecrire_journal(const char *message) {
    FILE *f = fopen("journal.log", "a");
    if (f == NULL) {
        return -1;
    }
    fprintf(f, "%s\n", message);
    return 0;
}
