def verifier(conn, login, empreinte):
    cur = conn.cursor()
    requete = "SELECT id FROM comptes WHERE login = '%s' AND empreinte = '%s'" % (login, empreinte)
    cur.execute(requete)
    return cur.fetchone() is not None
