f# 🎮 MySubMod - Installation Automatique

**Version:** Latest (Auto-Build)
**Minecraft:** 1.20.1
**Forge:** 47.3.39

---

## 📥 INSTALLATION AUTOMATIQUE (Recommandé)

### Configuration Prism Launcher

Le mod se met à jour **automatiquement** à chaque lancement de Minecraft!

**Étapes:**

1. **Créez votre instance** Minecraft Forge 1.20.1 dans Prism Launcher
2. **Clic droit** sur l'instance → **Edit** (Modifier)
3. **Settings** → **Custom Commands**
4. Dans **Pre-launch command**, collez cette ligne:

```bash
curl -L -o "$INST_MC_DIR/mods/mysubmod-1.0.0.jar" https://github.com/minecraftcomportementhumain/MOD1/releases/download/latest/mysubmod-1.0.0.jar
```

5. **Cliquez OK**
6. **C'est tout!** 🎉

### Résultat

- ✅ À chaque lancement, le mod se télécharge automatiquement
- ✅ Vous avez toujours la dernière version
- ✅ Fonctionne sur Windows, Linux ET Mac
- ✅ Aucune action manuelle nécessaire

---

## 🔧 Installation Manuelle (Alternative)

Si vous préférez télécharger manuellement:

1. Téléchargez `mysubmod-1.0.0.jar` ci-dessous
2. Placez-le dans le dossier `mods/` de votre instance:
   - **Windows:** `%APPDATA%\PrismLauncher\instances\VotreInstance\.minecraft\mods\`
   - **Linux:** `~/.local/share/PrismLauncher/instances/VotreInstance/.minecraft/mods/`
   - **Mac:** `~/Library/Application Support/PrismLauncher/instances/VotreInstance/.minecraft/mods/`

⚠️ **Note:** Vous devrez télécharger manuellement chaque nouvelle version.

---

## 📖 Documentation Complète

Pour plus de détails sur l'installation automatique:
- **[INSTALLATION_AUTO_UPDATE.md](https://github.com/minecraftcomportementhumain/MOD1/blob/main/INSTALLATION_AUTO_UPDATE.md)** - Guide complet avec dépannage

Pour la documentation du mod:
- **[README_SUBMOD.md](https://github.com/minecraftcomportementhumain/MOD1/blob/main/README_SUBMOD.md)** - Fonctionnalités du mod
- **[SYSTEME_AUTHENTIFICATION.md](https://github.com/minecraftcomportementhumain/MOD1/blob/main/SYSTEME_AUTHENTIFICATION.md)** - Système d'authentification
- **[CHANGELOG.md](https://github.com/minecraftcomportementhumain/MOD1/blob/main/CHANGELOG.md)** - Historique des modifications

---

## 🆕 Nouveautés de cette version

### Protection DoS et Optimisation Queue (21 octobre 2025)
- 🛡️ **Protection DoS**: Limites de 4 candidats/compte/IP et 10 candidats/IP global
- ♻️ **Éviction intelligente**: Candidats ≥20s automatiquement remplacés
- 🧹 **Nettoyage complet**: Tracking précis dans tous les scénarios
- 📊 **Comptage fiable**: Nombre exact de candidats en temps réel

Voir [CHANGELOG.md](https://github.com/minecraftcomportementhumain/MOD1/blob/main/CHANGELOG.md) pour l'historique complet.

---

## ❓ Support

**Problèmes d'installation?** Consultez le guide complet: [INSTALLATION_AUTO_UPDATE.md](https://github.com/minecraftcomportementhumain/MOD1/blob/main/INSTALLATION_AUTO_UPDATE.md)

**Besoin d'aide?** Ouvrez une [issue](https://github.com/minecraftcomportementhumain/MOD1/issues)

---

🤖 **Build automatique** via GitHub Actions - Toujours à jour!
