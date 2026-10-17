import os
from statistics import mean


def moyenne(notes):
    if not notes:
        return 0.0
    return mean(notes)


def lire_notes(chemin):
    with open(chemin, encoding="utf-8") as fichier:
        return [float(ligne) for ligne in fichier if ligne.strip()]


SEUIL = float(os.environ.get("SEUIL_REUSSITE", "10"))
