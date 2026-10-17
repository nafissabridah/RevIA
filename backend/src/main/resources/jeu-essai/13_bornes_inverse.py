def inverser(valeurs):
    resultat = []
    for i in range(len(valeurs) + 1):
        resultat.insert(0, valeurs[i])
    return resultat
