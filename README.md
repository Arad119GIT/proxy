Proxy
=====
Il s'agit d'un fork de [BungeeCord](https://github.com/SpigotMC/BungeeCord) au dernier stade compatible avec les protocoles 1.7.
Ce dernier a seulement reçus quelques modifications mineurs.

Modifications
-------------
- Suppression des 10 sec d'attente due à l'obsolescence de cette version
- Changement du serveur de sessions (auth server)
- Utilisation de `java.io` pour l'auth server (pour contourner un problème SSL lié à netty)
