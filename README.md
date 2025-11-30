Ce guide explique comment compiler et construire le mod Minecraft Forge à partir du code source.

## Prérequis

### Java Development Kit (JDK) 17
- **Version requise** : Java 17 (obligatoire)
- **Téléchargement** : [Adoptium Temurin 17](https://adoptium.net/temurin/releases/?version=17)
- Après installation, vérifiez avec : `java -version`

### Gradle
- **Version** : Gradle 8.8 (inclus via le wrapper `gradlew`)
- Pas besoin d'installer Gradle manuellement, le wrapper s'en charge

## Structure du Projet

```
forge-1.20.1-47.3.39-mdk/
├── build.gradle          # Configuration de build principale
├── gradle.properties     # Propriétés du projet
├── gradlew.bat          # Wrapper Gradle pour Windows
├── gradlew              # Wrapper Gradle pour Linux/Mac
├── src/
│   └── main/
│       ├── java/        # Code source Java
│       └── resources/   # Ressources (textures, fichiers de config)
└── build/               # Dossier de sortie (généré)
```

## Versions et Dépendances

| Composant | Version |
|-----------|---------|
| Minecraft | 1.20.1 |
| Forge | 47.3.39 |
| Java | 17 |
| Gradle | 8.8 |
| Mixin | 0.8.5 |

## Commandes de Build

### Windows

Ouvrez une invite de commande dans le dossier `forge-1.20.1-47.3.39-mdk` :

```batch
# Compiler le projet
.\gradlew.bat build

# Nettoyer et recompiler
.\gradlew.bat clean build

# Générer les fichiers Eclipse
.\gradlew.bat eclipse

# Lancer le client de test
.\gradlew.bat runClient

# Lancer le serveur de test
.\gradlew.bat runServer
```

### Linux / Mac

```bash
# Rendre le script exécutable (une seule fois)
chmod +x gradlew

# Compiler le projet
./gradlew build

# Nettoyer et recompiler
./gradlew clean build
```

## Résultat du Build

Après un build réussi, le fichier JAR du mod se trouve dans :
```
build/libs/mysubmod-1.0.0.jar
```

Ce fichier doit être copié dans le dossier `mods/` du serveur ou du client.

## Configuration Mémoire

Le fichier `gradle.properties` configure la mémoire pour Gradle :
```properties
org.gradle.jvmargs=-Xmx3G
```

Si vous avez des erreurs de mémoire, augmentez cette valeur (ex: `-Xmx4G`).

## Résolution de Problèmes

### Erreur : "Java version not found"
- Assurez-vous que `JAVA_HOME` pointe vers JDK 17
- Vérifiez que Java 17 est dans votre `PATH`

### Erreur : "Could not resolve dependencies"
```batch
.\gradlew.bat --refresh-dependencies build
```

### Erreur : Build très lent
```batch
# Utiliser le daemon Gradle (activer dans gradle.properties)
org.gradle.daemon=true
```

### Nettoyer complètement le cache
```batch
.\gradlew.bat clean
# Supprimer manuellement le dossier .gradle si nécessaire
```

## Notes Importantes

1. **Mappings** : Le projet utilise les mappings officiels Mojang (`official`)
2. **Mixin** : Le système Mixin est utilisé pour modifier le code Minecraft
3. **Encodage** : Tous les fichiers Java utilisent l'encodage UTF-8

## Premier Build

Pour un nouveau clone du projet :

```batch
# 1. Générer les sources Minecraft
.\gradlew.bat setup

# 2. Compiler le mod
.\gradlew.bat build
```

Le premier build téléchargera toutes les dépendances et prendra plus de temps.
