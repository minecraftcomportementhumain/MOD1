# MySubMod - Système de Sous-modes pour Minecraft

Ce mod ajoute un système de sous-modes qui fonctionne côté client et serveur pour Minecraft Forge 1.20.1.

## Fonctionnalités

### Sous-modes disponibles
1. **Salle d'attente** - Mode par défaut, activé automatiquement au démarrage du serveur
2. **Sous-mode 1** - Jeu de survie de 15 minutes sur îles carrées avec système de bonbons
3. **Sous-mode 2** - Mode personnalisé 2 (à implémenter)

### Interface Admin
- Accessible en appuyant sur la touche `M` en jeu
- Affiche le mode actuel
- Permet aux administrateurs de changer de sous-mode via des boutons
- Les non-administrateurs peuvent voir l'interface mais ne peuvent pas changer de mode

## Installation

1. Placez le fichier JAR compilé dans le dossier `mods` de votre client et serveur Minecraft
2. Démarrez le serveur - la salle d'attente se lance automatiquement
3. Les joueurs peuvent se connecter et voir l'interface avec `M`
4. Les données de jeu sont automatiquement sauvegardées dans `mysubmod_data/`

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

### Système de Logging
- **Enregistrement automatique** de toutes les sessions de jeu
- **Données détaillées** : positions, actions, événements
- **Structure organisée** : dossiers par session avec horodatage
- **Fichiers séparés** : logs individuels par joueur + événements globaux

### Interface Utilisateur
- **Sélection d'îles** : Interface graphique pour choisir son île de départ
- **Timer en jeu** : Affichage non-invasif du temps restant
- **Alertes automatiques** : Notifications aux moments clés
- **Restrictions visuelles** : Messages d'information pour les actions interdites

## Architecture Technique

- **Client-serveur** : Communication via packets réseau
- **Validation** : Vérification côté serveur des permissions admin
- **Interface** : GUI Minecraft avec boutons pour chaque sous-mode
- **Commandes** : Système de commandes intégré avec Brigadier
- **Génération procédurale** : Îles carrées avec décoration naturelle
- **Système de données** : Logging complet avec analyse comportementale
- **Gestion des effets** : Bonbons lumineux, barrières invisibles, restrictions joueurs

Le mod garantit que seuls les administrateurs autorisés peuvent effectuer des changements de sous-modes, tout en permettant à tous les joueurs de voir l'état actuel du système.