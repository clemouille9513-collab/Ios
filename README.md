# Écran de verrouillage iOS pour Samsung

Projet Android (Kotlin, sans dépendance externe à part androidx.core).

## Obtenir l'APK sans PC (GitHub)
1. Crée un dépôt GitHub et envoie-y tout le contenu de ce dossier (y compris le dossier `.github`).
2. Onglet **Actions** > **Build APK** > **Run workflow**.
3. Quand c'est vert, ouvre l'exécution et télécharge l'artefact `ioslock-apk` (un zip avec `app-debug.apk`).
4. Installe l'APK sur le téléphone (autorise les sources inconnues).

## Avec Android Studio (PC)
Ouvre le dossier, laisse Gradle se synchroniser, puis Build > Build APK(s).

## Réglages sur le téléphone
1. Ouvre l'appli « Écran iOS ».
2. Autorise l'affichage par-dessus les autres applis, les notifications, et désactive l'optimisation de batterie.
3. (Facultatif) choisis un code. Vide = ton verrouillage Samsung (empreinte / code) est demandé après le balayage.
4. Active l'interrupteur. Éteins puis rallume l'écran : l'écran iOS apparaît.

## Limites
- Android n'autorise pas à remplacer l'écran de verrouillage système : l'appli s'affiche par-dessus.
- Garde un verrouillage Samsung (code ou empreinte) si tu veux une vraie sécurité : le bouton Accueil peut contourner l'écran quand le Samsung est réglé sur « Balayer ».
- Pas encore : notifications sur l'écran, fond d'écran personnalisé, widgets.
