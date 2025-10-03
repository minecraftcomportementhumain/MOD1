# MySubMod - Système de Sous-modes pour Minecraft

Ce mod ajoute un système de sous-modes qui fonctionne côté client et serveur pour Minecraft Forge 1.20.1.

## Fonctionnalités

### Sous-modes disponibles
1. **Salle d'attente** - Mode par défaut, activé automatiquement au démarrage du serveur
2. **Sous-mode 1** - Jeu de survie de 15 minutes sur 4 îles avec système de bonbons et spawn points
3. **Sous-mode 2** - Mode personnalisé 2 (à implémenter)

### Interface Admin
- Accessible en appuyant sur la touche `M` en jeu
- Affiche le mode actuel
- Permet aux administrateurs de changer de sous-mode via des boutons
- Bouton 📊 pour gérer les logs (télécharger/supprimer)
- Bouton 📁 pour gérer les fichiers de spawn (upload/sélection/suppression)
- Les non-administrateurs peuvent voir l'interface mais ne peuvent pas changer de mode

## Installation

1. Placez le fichier JAR compilé dans le dossier `mods` de votre client et serveur Minecraft
2. Démarrez le serveur - la salle d'attente se lance automatiquement
3. Les joueurs peuvent se connecter et voir l'interface avec `M`
4. Les données de jeu sont automatiquement sauvegardées dans `mysubmod_data/`
5. Les fichiers de configuration de spawn sont dans `candy_spawn_configs/`

## Commandes Serveur

### Gestion des sous-modes
```
/submode set <mode>        - Change le sous-mode (waiting/attente, 1, 2)
/submode current           - Affiche le mode actuel
```

### Gestion des administrateurs
```
/submode admin add <joueur>     - Ajoute un administrateur
/submode admin remove <joueur>  - Supprime un administrateur
/submode admin list             - Liste les administrateurs
```

## Permissions

- Les administrateurs du serveur (niveau OP 2+) peuvent gérer les admins et changer les modes
- Les admins ajoutés via commande peuvent changer les sous-modes
- Tous les joueurs peuvent voir l'interface mais seuls les admins peuvent l'utiliser

## Utilisation

1. **Démarrage automatique** : La salle d'attente se lance automatiquement
2. **Changement de mode** :
   - Via l'interface (touche `M`) pour les admins
   - Via commandes serveur `/submode set <mode>`
3. **Synchronisation** : Tous les clients connectés reçoivent automatiquement le changement de mode

## Fonctionnalités Avancées

### Système de Spawn Points
- **Génération aléatoire** : Spawn points générés automatiquement à chaque partie
- **Espacement** : Minimum 40 blocs entre chaque spawn point
- **Par île** : 1 point (Petite), 2 points (Moyenne), 3 points (Large), 4 points (Très Grande)
- **Fichiers de configuration** : Format `temps,quantité,île,spawn_point`

### HUD en Temps Réel
- **Affichage non-invasif** : Coin supérieur droit de l'écran
- **Bonbons disponibles** : Compte par île avec couleurs distinctives
- **Mise à jour automatique** : Toutes les 2 secondes
- **Désactivation automatique** : À la fin de la partie

### Système de Logging
- **Enregistrement automatique** de toutes les sessions de jeu
- **Données détaillées** : positions, actions, événements, choix d'îles
- **Structure organisée** : dossiers par session avec horodatage
- **Fichiers séparés** : logs individuels par joueur + événements globaux

### Interface Utilisateur
- **Sélection d'îles** : Interface graphique pour choisir son île de départ (4 options)
- **Sélection de fichiers** : Upload et gestion des fichiers de configuration de spawn
- **Timer en jeu** : Affichage non-invasif du temps restant
- **Alertes automatiques** : Notifications aux moments clés
- **Restrictions visuelles** : Messages d'information pour les actions interdites

### Gestion des Fichiers de Spawn
- **Upload** : Interface pour téléverser des fichiers de configuration personnalisés
- **Validation** : Vérification automatique du format et des valeurs
- **Suppression** : Gestion des fichiers via interface graphique (default.txt protégé)
- **Sélection** : Liste défilante avec sélection par clic
- **Actualisation** : Bouton pour rafraîchir la liste des fichiers

### Gestion des Logs
- **Téléchargement** : Logs compressés en ZIP dans le dossier Downloads
- **Téléchargement en masse** : Option pour télécharger tous les logs en un fichier
- **Suppression sélective** : Interface pour supprimer des sessions individuelles
- **Suppression en masse** : Option pour nettoyer tous les logs
- **Liste défilante** : Interface moderne avec sélection par clic

## Architecture Technique

### Communication Réseau
- **Client-serveur** : Communication via packets réseau
- **Validation** : Vérification côté serveur des permissions admin
- **Synchronisation** : Mise à jour en temps réel des compteurs de bonbons

### Interface Graphique
- **GUI Minecraft** : Boutons pour chaque sous-mode
- **HUD Overlay** : Affichage des ressources disponibles
- **Écrans personnalisés** : Sélection d'îles et de fichiers

### Système de Jeu
- **Génération procédurale** : 4 îles carrées autour d'un carré central (20x20)
- **Distances** : 360 blocs entre le centre et chaque île
- **Spawn points** : Système aléatoire avec contraintes de distance
- **Téléportation sécurisée** : Chargement de chunks avant téléportation

### Gestion des Données
- **Logging complet** : Analyse comportementale détaillée
- **Tracking des ressources** : Comptage en temps réel par île
- **Configuration flexible** : Fichiers de spawn personnalisables

### Effets et Restrictions
- **Bonbons lumineux** : Effet de glowing pour visibilité
- **Barrières invisibles** : Protection des îles et chemins
- **Restrictions joueurs** : Limitations des actions en jeu
- **Fin de partie** : Automatique si tous morts ou timer expiré

## Nouveautés de la Dernière Version

### Améliorations Majeures
- **Hologrammes** : Indicateurs directionnels au-dessus des tours de laine au carré central
- **Protection améliorée** : Blocage de tous les items au sol (sauf bonbons) sur îles et chemins
- **Sprint désactivé** : Vitesse de sprint réduite à la vitesse de marche normale
- **Gestion des logs** : Interface complète de téléchargement et suppression des logs
- **Interface modernisée** : Listes défilantes avec sélection par clic pour fichiers et logs
- **Correction HUD** : Le HUD ne persiste plus après déconnexion/reconnexion

### Fonctionnalités du Sous-mode 1
- **4 îles** de tailles différentes (60x60, 90x90, 120x120, 150x150)
- **Carré central** de spawn (20x20) avec tours de laine colorées
- **Système de spawn points** aléatoires par île
- **HUD des ressources** disponibles par île en temps réel
- **Logging complet** du choix d'île de chaque joueur
- **Téléportation sécurisée** avec chargement de chunks
- **Fin automatique** si tous les joueurs meurent ou timer expiré
- **Protection complète** : Aucun bloc ne peut être cassé/placé, aucun craft possible
- **Monde vide** : Serveur configuré avec monde void par défaut
- **Visibilité augmentée** : Distance de rendu des entités augmentée à 300%

Le mod garantit que seuls les administrateurs autorisés peuvent effectuer des changements de sous-modes, tout en permettant à tous les joueurs de voir l'état actuel du système et de participer pleinement aux parties.
