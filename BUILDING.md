# Compiler AdAway

## Outils à installer une seule fois

1. **Android Studio**, dernière version stable. Elle doit prendre en charge le plugin Android 9.4.
2. **Le NDK Android 30.0.16248370** : Android Studio > Settings > Languages & Frameworks > Android SDK > onglet "SDK Tools" > "NDK (Side by side)". Cochez "Show Package Details" pour choisir cette version exacte.
3. **Rust**, avec rustup : https://rustup.rs. La bonne version de Rust (1.99.0) et les cibles Android sont installées automatiquement à la première compilation, d'après `rust/rust-toolchain.toml`.
4. **cargo-ndk**, qui compile le moteur Rust pour Android. Dans un terminal :

   ```
   cargo install cargo-ndk --locked
   ```

Après l'installation de Rust, redémarrez Android Studio pour qu'il trouve la commande `cargo`.

## Signature

Pour signer les versions debug et release, placez un fichier `keystore.properties` à la racine du projet :

```
storeFile=C:/chemin/vers/votre.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Utilisez la même clé que pour les versions 6.x : sans elle, la nouvelle version ne peut pas s'installer par-dessus l'ancienne.

## Compiler

- Depuis Android Studio : bouton "Run", ou Build > Generate App Bundles or APKs.
- En ligne de commande : `gradlew assembleRelease` (sous Windows, `gradlew.bat assembleRelease`).

## Tests

- Tests de l'app : `gradlew testDebugUnitTest`
- Tests du moteur Rust, depuis le dossier `rust` : `cargo test`
