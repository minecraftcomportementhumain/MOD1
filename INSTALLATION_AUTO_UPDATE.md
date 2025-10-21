# 🔄 Configuration de la Mise à Jour Automatique au Lancement

Ce guide explique comment configurer Prism Launcher pour télécharger automatiquement la dernière version du mod **à chaque lancement** de l'instance.

---

## 🎯 Avantages

- ✅ Pas besoin de lancer manuellement `update-mod.bat`
- ✅ Toujours la dernière version du mod
- ✅ Mise à jour transparente pour les joueurs
- ✅ Fonctionne sur Windows, Linux et Mac

---

## 📋 Configuration dans Prism Launcher

### Étape 1: Ouvrir les Paramètres de l'Instance

1. Ouvrez **Prism Launcher**
2. **Clic droit** sur votre instance `ÉtudeComportementHumain`
3. Cliquez sur **Edit** (ou **Modifier**)

### Étape 2: Aller dans Settings (Paramètres)

1. Dans le menu de gauche, cliquez sur **Settings**
2. Scrollez jusqu'à **Custom Commands** (Commandes personnalisées)

### Étape 3: Configurer la Pre-Launch Command

#### Pour Windows:

Collez cette commande dans **Pre-launch command:**

```batch
cmd /c "curl -L -o "$INST_MC_DIR\mods\mysubmod-1.0-SNAPSHOT.jar" https://github.com/minecraftcomportementhumain/MOD1/releases/download/latest/mysubmod-1.0-SNAPSHOT.jar"
```

#### Pour Linux/Mac:

Collez cette commande dans **Pre-launch command:**

```bash
curl -L -o "$INST_MC_DIR/mods/mysubmod-1.0-SNAPSHOT.jar" https://github.com/minecraftcomportementhumain/MOD1/releases/download/latest/mysubmod-1.0-SNAPSHOT.jar
```

### Étape 4: Sauvegarder

1. Cliquez sur **OK** ou **Appliquer**
2. Fermez la fenêtre de configuration

---

## ✅ Test de la Configuration

1. **Lancez l'instance** `ÉtudeComportementHumain`
2. **Observez:** Une fenêtre de terminal devrait apparaître brièvement
3. **Résultat:** Le mod est téléchargé automatiquement avant le lancement!

---

## 🔍 Vérification

Pour vérifier que le mod se télécharge bien:

**Windows:**
```
%APPDATA%\PrismLauncher\instances\ÉtudeComportementHumain\.minecraft\mods\mysubmod-1.0-SNAPSHOT.jar
```

**Linux:**
```
~/.local/share/PrismLauncher/instances/ÉtudeComportementHumain/.minecraft/mods/mysubmod-1.0-SNAPSHOT.jar
```

**Mac:**
```
~/Library/Application Support/PrismLauncher/instances/ÉtudeComportementHumain/.minecraft/mods/mysubmod-1.0-SNAPSHOT.jar
```

Vérifiez la date de modification du fichier - elle devrait correspondre à maintenant!

---

## 🎮 Fonctionnement

### Ce qui se passe à chaque lancement:

1. ✅ Prism Launcher démarre
2. ✅ **Pre-launch command** s'exécute
3. ✅ `curl` télécharge la dernière version depuis GitHub
4. ✅ Le fichier `.jar` est remplacé dans `mods/`
5. ✅ Minecraft démarre avec la dernière version!

### Temps d'exécution:

- ~2-5 secondes de plus au lancement
- Dépend de la vitesse de connexion internet
- Le jeu démarre automatiquement après le téléchargement

---

## ⚙️ Configuration Avancée (Optionnel)

### Afficher un Message de Progression

Si vous voulez voir le progrès du téléchargement, modifiez la commande:

**Windows:**
```batch
cmd /c "echo Mise a jour du mod... & curl -L --progress-bar -o "$INST_MC_DIR\mods\mysubmod-1.0-SNAPSHOT.jar" https://github.com/minecraftcomportementhumain/MOD1/releases/download/latest/mysubmod-1.0-SNAPSHOT.jar & echo Mod mis a jour!"
```

**Linux/Mac:**
```bash
echo "Mise à jour du mod..." && curl -L --progress-bar -o "$INST_MC_DIR/mods/mysubmod-1.0-SNAPSHOT.jar" https://github.com/minecraftcomportementhumain/MOD1/releases/download/latest/mysubmod-1.0-SNAPSHOT.jar && echo "Mod mis à jour!"
```

### Vérifier si une Mise à Jour est Nécessaire (Économiser Bande Passante)

Pour ne télécharger que si le fichier a changé:

**Windows:**
```batch
cmd /c "curl -L -z "$INST_MC_DIR\mods\mysubmod-1.0-SNAPSHOT.jar" -o "$INST_MC_DIR\mods\mysubmod-1.0-SNAPSHOT.jar" https://github.com/minecraftcomportementhumain/MOD1/releases/download/latest/mysubmod-1.0-SNAPSHOT.jar"
```

**Linux/Mac:**
```bash
curl -L -z "$INST_MC_DIR/mods/mysubmod-1.0-SNAPSHOT.jar" -o "$INST_MC_DIR/mods/mysubmod-1.0-SNAPSHOT.jar" https://github.com/minecraftcomportementhumain/MOD1/releases/download/latest/mysubmod-1.0-SNAPSHOT.jar
```

L'option `-z` télécharge seulement si le fichier serveur est plus récent.

---

## ❓ Dépannage

### "curl: command not found"

**Windows:**
- Windows 10/11 inclut `curl` par défaut
- Si l'erreur persiste, installez Git for Windows: https://git-scm.com/download/win

**Linux:**
```bash
sudo apt install curl  # Ubuntu/Debian
sudo yum install curl  # CentOS/RHEL
```

**Mac:**
- `curl` est préinstallé sur macOS

### Le mod ne se télécharge pas

1. Vérifiez votre connexion internet
2. Vérifiez que le repository est **public**: https://github.com/minecraftcomportementhumain/MOD1
3. Vérifiez que la release existe: https://github.com/minecraftcomportementhumain/MOD1/releases
4. Essayez de télécharger manuellement avec `curl` dans un terminal

### Le jeu démarre avec l'ancienne version

1. Supprimez manuellement l'ancien `.jar` du dossier `mods/`
2. Relancez l'instance
3. Vérifiez les logs de Prism (onglet "Console" ou "Log")

### Multiples versions du mod dans mods/

Si vous avez plusieurs fichiers `.jar`:
1. Supprimez tous les anciens fichiers `.jar` du mod
2. Gardez seulement `mysubmod-1.0-SNAPSHOT.jar`

---

## 🔒 Sécurité

### Le téléchargement est-il sûr?

✅ **Oui**, car:
- Le mod provient de votre propre repository GitHub
- URL HTTPS sécurisée
- GitHub vérifie l'intégrité des fichiers
- Pas d'exécution de code arbitraire

### Peut-on désactiver la mise à jour automatique?

Oui, il suffit de:
1. Éditer l'instance
2. Settings → Custom Commands
3. Supprimer la **Pre-launch command**
4. OK

---

## 📊 Comparaison des Méthodes

| Méthode | Automatique | Facilité | Contrôle |
|---------|-------------|----------|----------|
| **Pre-Launch Command** | ✅ | ⭐⭐⭐⭐⭐ | Moyen |
| **Script Manuel** | ❌ | ⭐⭐⭐ | Élevé |
| **Téléchargement Manuel** | ❌ | ⭐ | Total |

---

## 🎯 Recommandation

**Pour les participants à l'expérience:** Utilisez la **Pre-Launch Command** pour garantir qu'ils ont toujours la dernière version sans effort.

**Pour les testeurs/développeurs:** Utilisez le **script manuel** (`update-mod.bat`) pour plus de contrôle.

---

## ✅ Checklist de Configuration

- [ ] Prism Launcher installé
- [ ] Instance `ÉtudeComportementHumain` créée
- [ ] Pre-launch command configurée
- [ ] Instance lancée une fois pour tester
- [ ] Mod téléchargé avec succès
- [ ] Minecraft démarre correctement

---

**Maintenant, chaque lancement garantit la dernière version du mod!** 🚀

---

## 📞 Support

Si vous rencontrez des problèmes, vérifiez:
1. Les logs de Prism Launcher (onglet Console)
2. Que `curl` est installé et fonctionne
3. Que le repository GitHub est accessible

**Besoin d'aide?** Contactez l'administrateur du serveur.
