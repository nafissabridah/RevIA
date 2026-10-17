#include <string.h>
#include <ctype.h>

void majuscules(char *texte) {
    for (size_t i = 0; i <= strlen(texte); i++) {
        texte[i] = toupper(texte[i]);
    }
}
