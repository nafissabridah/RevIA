import sqlite3


def commandes_client(conn, client_id):
    cursor = conn.cursor()
    cursor.execute(f"SELECT id, total FROM commandes WHERE client_id = {client_id}")
    return cursor.fetchall()
