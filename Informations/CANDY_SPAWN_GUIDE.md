# Guide d'Utilisation des Fichiers de Spawn de Bonbons

## Introduction

Les fichiers de spawn de bonbons permettent de contrôler précisément **quand**, **où** et **combien** de bonbons apparaissent pendant une partie du SubMode1.

## Ajout de Fichiers de Configuration

Les fichiers de spawn sont ajoutés via l'interface graphique :
1. Appuyez sur **M** pour ouvrir le menu de contrôle
2. Cliquez sur le bouton **📁** (Charger depuis disque)
3. Sélectionnez votre fichier `.txt` depuis votre ordinateur
4. Le fichier sera automatiquement validé et uploadé vers `run/candy_spawn_configs/`

**Note** : Les fichiers sont stockés côté serveur dans `run/candy_spawn_configs/` mais vous ne devez **jamais** les placer manuellement dans ce dossier. Utilisez toujours l'interface d'upload.

## Format du Fichier

### Syntaxe de Base

Chaque ligne du fichier définit un spawn de bonbons:
```
temps_en_secondes,nombre_bonbons,x,y,z
```

### Paramètres

1. **temps_en_secondes** (0-900)
   - Moment du spawn depuis le début de la partie
   - Maximum: 900 secondes (15 minutes)
   - Exemple: `60` = spawn après 1 minute

2. **nombre_bonbons** (1-100)
   - Quantité de bonbons à spawner
   - Les bonbons sont dispersés dans un rayon de 3 blocs autour de la position
   - Exemple: `10` = 10 bonbons

3. **x,y,z** (coordonnées exactes)
   - **x**: Coordonnée Est-Ouest
   - **y**: Hauteur (doit être entre 100 et 120)
   - **z**: Coordonnée Nord-Sud
   - Les coordonnées doivent être sur l'une des 4 îles

### Commentaires

Les lignes commençant par `#` sont des commentaires et sont ignorées:
```
# Ceci est un commentaire
60,5,0,101,-360  # Spawn de 5 bonbons après 60 secondes
```

## Limites des Îles

Les coordonnées doivent être à l'intérieur des limites d'une île:

### SMALL (Petite Île - 60×60)
- **Centre**: (0, -360)
- **X**: -30 à +30
- **Z**: -390 à -330
- **Hauteur**: Y = 100

### MEDIUM (Île Moyenne - 90×90)
- **Centre**: (360, 0)
- **X**: 315 à 405
- **Z**: -45 à +45
- **Hauteur**: Y = 100

### LARGE (Grande Île - 120×120)
- **Centre**: (0, 360)
- **X**: -60 à +60
- **Z**: 300 à 420
- **Hauteur**: Y = 100

### EXTRA_LARGE (Très Grande Île - 150×150)
- **Centre**: (-360, 0)
- **X**: -435 à -285
- **Z**: -75 à +75
- **Hauteur**: Y = 100

## Contraintes de Validation

Le système valide automatiquement chaque ligne:

✅ **Accepté si:**
- Temps entre 0 et 900 secondes
- Nombre de bonbons entre 1 et 100
- Y entre 100 et 120
- X et Z dans les limites d'une île

❌ **Rejeté si:**
- Paramètres manquants ou invalides
- Coordonnées hors des îles
- Valeurs hors limites

Si une seule ligne est invalide, **tout le fichier est rejeté**.

## Exemples

### Exemple 1: Spawn Simple
```
# Un spawn toutes les minutes sur différentes îles
60,5,0,101,-360        # 5 bonbons sur SMALL après 1 min
120,10,360,101,0       # 10 bonbons sur MEDIUM après 2 min
180,15,0,101,360       # 15 bonbons sur LARGE après 3 min
240,20,-360,101,0      # 20 bonbons sur EXTRA_LARGE après 4 min
```

### Exemple 2: Progression Croissante
```
# Augmentation progressive du nombre de bonbons
60,5,0,101,-360
120,10,0,101,-360
180,20,0,101,-360
240,30,0,101,-360
300,40,0,101,-360
360,50,0,101,-360
```

### Exemple 3: Multi-îles Simultané
```
# Spawn sur toutes les îles en même temps
100,10,0,101,-360      # SMALL
100,10,360,101,0       # MEDIUM
100,10,0,101,360       # LARGE
100,10,-360,101,0      # EXTRA_LARGE
```

### Exemple 4: Coins de l'Île
```
# Spawner aux 4 coins d'une île
60,5,-30,101,-390      # Coin Nord-Ouest SMALL
60,5,30,101,-390       # Coin Nord-Est SMALL
60,5,-30,101,-330      # Coin Sud-Ouest SMALL
60,5,30,101,-330       # Coin Sud-Est SMALL
```

## Utilisation In-Game

### 1. Préparer le Fichier
- Créez votre fichier `.txt` sur votre ordinateur (n'importe où)
- Respectez le format et les limites (voir sections précédentes)
- Testez avec peu de spawns d'abord

### 2. Upload du Fichier
- Appuyez sur **M** pour ouvrir le menu de contrôle
- Cliquez sur le bouton **📁** (Charger depuis disque)
- Entrez le nom du fichier (sans extension)
- Collez le contenu de votre fichier dans la zone de texte
- Cliquez sur "✓ Confirmer l'upload"
- Le fichier sera automatiquement validé et ajouté à la liste

### 3. Sélectionner le Fichier et Lancer la Partie
- Appuyez sur **N** pour ouvrir le menu de sélection
- Choisissez votre fichier dans la liste
- Cliquez sur "✓ Confirmer la sélection"
- **La partie démarre automatiquement** dès la sélection

### 4. Actualiser la Liste
Si vous uploadez un nouveau fichier pendant que le jeu tourne :
- Appuyez sur **N**
- Cliquez sur "🔄 Actualiser"
- Votre nouveau fichier apparaîtra dans la liste

## Conseils et Bonnes Pratiques

### ✨ Conception de Bon Niveau

1. **Équilibrage**
   - Variez les îles pour encourager l'exploration
   - Augmentez progressivement la quantité
   - Répartissez dans le temps (pas tout au début)

2. **Stratégie**
   - Grandes îles = plus d'espace = spawns plus éparpillés
   - Petites îles = compétition plus intense
   - Coordonnées précises = contrôle total

3. **Performance**
   - Ne spawnez pas 100 bonbons à la fois
   - Espacez les spawns dans le temps
   - Évitez trop de spawns simultanés

### 🔧 Débogage

Si l'upload échoue :
1. Vérifiez les logs du serveur pour erreurs de validation
2. Assurez-vous que le format est correct : `temps,quantité,x,y,z`
3. Vérifiez que toutes les valeurs sont dans les limites (voir "Contraintes de Validation")
4. Testez ligne par ligne pour identifier quelle ligne pose problème

Si les bonbons n'apparaissent pas en jeu :
1. Vérifiez que le fichier a été sélectionné avec la touche **N**
2. Assurez-vous que les coordonnées sont correctes (sur une île)
3. Vérifiez que Y est bien entre 100 et 120
4. Consultez les logs du serveur pour voir les spawns

### 📊 Test de Validation

Créez un fichier `test.txt` pour vérifier que tout fonctionne :
```
10,5,0,101,-360     # Centre SMALL après 10 secondes
20,5,360,101,0      # Centre MEDIUM après 20 secondes
30,5,0,101,360      # Centre LARGE après 30 secondes
40,5,-360,101,0     # Centre EXTRA_LARGE après 40 secondes
```

Uploadez-le via l'interface (bouton 📁), puis sélectionnez-le avec **N** pour lancer la partie.

## Fichier Par Défaut

### default.txt
- Fichier de configuration par défaut fourni avec le mod
- Progression équilibrée sur 14 minutes (840 secondes)
- Spawns sur toutes les îles avec quantités progressives
- **Ne peut pas être supprimé** (protection intégrée)
- Toujours disponible dans la liste de sélection

**Note** : Si vous voulez créer vos propres fichiers de test, utilisez l'interface d'upload (bouton 📁) pour les ajouter.

## Carte des Îles

```
                 Nord (Z négatif)
                      ↑
                      |
              ┌───────────┐
              │   SMALL   │ (0, -360)
              │   60×60   │
              └───────────┘
                      |
                      |
Ouest ←───────────────┼───────────────→ Est
                      |
  ┌───────────┐       |       ┌───────────┐
  │EXTRA_LARGE│       |       │  MEDIUM   │
  │  150×150  │       |       │   90×90   │
  └───────────┘       |       └───────────┘
  (-360, 0)           |         (360, 0)
                      |
              ┌───────────┐
              │   LARGE   │ (0, 360)
              │  120×120  │
              └───────────┘
                      |
                      ↓
                 Sud (Z positif)
```

## Ressources

- **Logs**: `run/logs/latest.log` - Vérifiez les erreurs de validation
- **Données de partie**: `run/game_logs/` - Statistiques après chaque partie
- **Documentation**: `SUBMODE1_GUIDE.md` - Guide complet du SubMode1

---

**Bon jeu! 🍬**
