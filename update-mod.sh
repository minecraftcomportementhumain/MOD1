#!/bin/bash

echo "========================================"
echo "  Mise à jour automatique du mod"
echo "========================================"
echo ""

# Configuration
GITHUB_USER="minecraftcomportementhumain"
GITHUB_REPO="MOD1"
MOD_NAME="mysubmod-1.0.0.jar"
PRISM_INSTANCE_NAME="ÉtudeComportementHumain"

# Détection automatique du système
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    PRISM_DIR="$HOME/Library/Application Support/PrismLauncher/instances/$PRISM_INSTANCE_NAME/.minecraft/mods"
else
    # Linux
    PRISM_DIR="$HOME/.local/share/PrismLauncher/instances/$PRISM_INSTANCE_NAME/.minecraft/mods"
fi

DOWNLOAD_URL="https://github.com/$GITHUB_USER/$GITHUB_REPO/releases/download/latest/$MOD_NAME"

echo "Configuration:"
echo "- GitHub: $GITHUB_USER/$GITHUB_REPO"
echo "- Instance: $PRISM_INSTANCE_NAME"
echo "- Destination: $PRISM_DIR"
echo ""

# Vérifier si le dossier existe
if [ ! -d "$PRISM_DIR" ]; then
    echo "ERREUR: Le dossier de mods n'existe pas!"
    echo "Vérifiez le nom de votre instance Prism Launcher."
    echo "Chemin: $PRISM_DIR"
    exit 1
fi

echo "Téléchargement du mod depuis GitHub..."
curl -L -o "$PRISM_DIR/$MOD_NAME" "$DOWNLOAD_URL"

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================"
    echo "  Mod mis à jour avec succès!"
    echo "========================================"
    echo ""
    echo "Le mod a été téléchargé dans:"
    echo "$PRISM_DIR/$MOD_NAME"
    echo ""
    echo "Vous pouvez maintenant lancer Minecraft."
else
    echo ""
    echo "ERREUR: Le téléchargement a échoué."
    echo "Vérifiez votre connexion internet et la configuration GitHub."
    exit 1
fi
