# Changelog - MySubMod

## Dernières modifications importantes

### Changements récents du gameplay

#### Îles carrées au lieu de circulaires
- **Forme** : Conversion de toutes les îles de circulaires à carrées
- **Tailles** : Petite (15x15), Moyenne (25x25), Grande (35x35)
- **Barrières** : Système de barrières invisibles repensé avec ouvertures pour les chemins
- **Chemins** : Chemins en pierre reliant les îles entre elles

#### Système de santé ajusté
- **Santé initiale** : 100% santé au début de partie (inchangé)
- **Faim initiale** : 50% faim au lieu de 100% (nouveau)
- **Dégradation** : -0.5 cœur toutes les 10 secondes (inchangé)

#### Amélioration des bonbons
- **Expiration** : Changé de 30 secondes à 2 minutes
- **Visibilité** : Ajout d'effet lumineux et flottement pour visibilité à distance
- **Distribution** : 35 bonbons par joueur répartis sur 15 minutes
- **Répartition** : 50% grande île, 30% moyenne, 20% petite île

#### Durée de jeu
- **Timer** : 15 minutes de jeu (retour à la valeur originale)
- **Distribution des bonbons** : Étalée sur toute la durée de la partie

### Corrections de bugs

#### Barrières et chemins
- **Problème** : Barrières trop lâches, chemins inaccessibles
- **Solution** : Correction de la logique de placement des barrières avec ouvertures précises aux connexions des chemins
- **Détails** : Barrières placées exactement au bord des îles avec ouvertures de 3 blocs de large

#### Visibilité des bonbons
- **Problème** : Bonbons difficiles à voir de loin
- **Solution** : Ajout d'effet lumineux (`setGlowingTag(true)`) et flottement (`setNoGravity(true)`)
- **Résultat** : Bonbons visibles à travers les blocs et plus faciles à repérer

#### Système de logging
- **Amélioration** : Logs plus détaillés avec santé après consommation de bonbons
- **Structure** : Organisation claire dans `mysubmod_data/submode1_game_[timestamp]/`
- **Données** : Positions, consommations, ramassages, changements de santé, morts, sélections d'îles

### Architecture technique

#### Génération d'îles
- **IslandGenerator.java** : Adaptation pour îles carrées avec bordures naturelles
- **Barrières** : Logique de placement avec vérification des connexions de chemins
- **Décorations** : Arbres, fleurs, herbe adaptés aux formes carrées

#### Gestion des bonbons
- **SubMode1CandyManager.java** :
  - Système de distribution proportionnelle
  - Gestion de l'expiration à 2 minutes
  - Effets visuels améliorés
  - Spawn sur surfaces d'herbe uniquement

#### Logging des données
- **SubMode1DataLogger.java** :
  - Sessions horodatées
  - Logs individuels par joueur
  - Événements globaux
  - Données précises au milliseconde

### Prochaines améliorations prévues
- Implémentation du Sous-mode 2
- Interface d'administration avancée
- Outils d'analyse des données collectées
- Système de replay des parties

---

*Dernière mise à jour : 28 septembre 2025*