# Guide de la Salle d'Attente - MySubMod

## Vue d'ensemble

La salle d'attente est le sous-mode par défaut qui se lance automatiquement au démarrage du serveur. C'est un environnement contrôlé où les joueurs attendent qu'un administrateur lance un autre sous-mode.

## Fonctionnalités de la Salle d'Attente

### Structure
- **Plateforme rectangulaire** : 20x20 blocs en pierre taillée
- **Position** : Coordonnées (0, 100, 0) dans l'Overworld
- **Barrières invisibles** : Empêchent les joueurs de quitter la plateforme
- **Hauteur** : 3 blocs de barrières autour de la plateforme

### Restrictions des Joueurs

Lorsqu'un joueur est dans la salle d'attente, il **NE PEUT PAS** :
- ❌ Attaquer d'autres joueurs ou entités
- ❌ Interagir avec des blocs (clic droit)
- ❌ Utiliser des objets
- ❌ Casser des blocs
- ❌ Placer des blocs
- ❌ Fabriquer des objets
- ❌ Jeter des objets
- ❌ Ramasser des objets
- ❌ Quitter la plateforme (téléportation automatique au centre)

### Ce que les joueurs PEUVENT faire :
- ✅ Se déplacer sur la plateforme
- ✅ Communiquer via le chat
- ✅ Regarder autour d'eux

## Gestion des Inventaires

### Sauvegarde Automatique
- L'inventaire du joueur est **automatiquement sauvegardé** lors de l'entrée en salle d'attente
- L'inventaire devient **vide** pendant la salle d'attente
- L'inventaire est **restauré automatiquement** lors de la sortie du mode

### Moments de Restauration
1. Changement vers un autre sous-mode
2. Déconnexion du joueur
3. Arrêt du serveur

## Messages et Notifications

### Messages Périodiques
- **Fréquence** : Toutes les minutes
- **Message** : "[Salle d'attente] Veuillez attendre qu'un administrateur lance un jeu"
- **Couleur** : Jaune (§e)

### Messages de Restriction
Les joueurs reçoivent des messages d'avertissement lorsqu'ils tentent des actions interdites :
- "Vous ne pouvez pas attaquer en salle d'attente"
- "Vous ne pouvez pas interagir avec les blocs en salle d'attente"
- "Vous ne pouvez pas quitter la plateforme de la salle d'attente"

## Gestion Automatique

### Activation
- **Démarrage serveur** : Activé automatiquement
- **Nouveaux joueurs** : Téléportés automatiquement s'ils se connectent pendant ce mode
- **Génération** : La plateforme est créée automatiquement

### Désactivation
- **Changement de mode** : Nettoyage automatique lors du passage à un autre sous-mode
- **Restauration** : Tous les inventaires sont restaurés
- **Suppression** : La plateforme est supprimée pour éviter l'accès depuis d'autres modes

## Architecture Technique

### Classes Principales
- `WaitingRoomManager` : Gestion de la plateforme et des joueurs
- `WaitingRoomEventHandler` : Gestion des restrictions et événements
- `WaitingRoomTicker` : Vérification périodique des limites de la plateforme

### Intégration
- **SubModeManager** : Active/désactive automatiquement la salle d'attente
- **Network** : Synchronisation avec les clients
- **Events** : Écoute de tous les événements pertinents

## Commandes Administratives

```bash
# Activer manuellement la salle d'attente
/submode set waiting

# Vérifier le mode actuel
/submode current

# Changer vers un autre mode (désactive automatiquement la salle d'attente)
/submode set 1
/submode set 2
```

## Sécurité

- **Validation côté serveur** : Toutes les restrictions sont appliquées côté serveur
- **Permissions** : Seuls les administrateurs peuvent changer de mode
- **Isolation** : Les joueurs ne peuvent pas échapper à la plateforme
- **Sauvegarde** : Les inventaires sont protégés contre la perte

La salle d'attente garantit un environnement sûr et contrôlé pour tous les joueurs en attendant le lancement des activités principales du serveur.