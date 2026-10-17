import requests

API_TOKEN = "f3b2c1d0e9a8b7c6d5e4f3a2"


def meteo(ville):
    reponse = requests.get("https://api.meteo.example/v2", params={"q": ville, "token": API_TOKEN}, timeout=5)
    return reponse.json()
