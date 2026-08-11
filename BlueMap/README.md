# Auth/BlueMap

Adding things to BlueMap was the reason for this entire project.
That's why this monorepo contains this folder with BlueMap addons that utilize the Authentication technology.
These also serve as example plugins and addons for developers to create their own extensions of this ecosystem.

The addons support both Spigot/Paper and Fabric:

- [Integration](./Integration) exposes the authenticated player to BlueMap addons.
- [Chat](./Chat) bridges Minecraft chat and the web map.
- [MarkerEditor](./MarkerEditor) provides private and permission-controlled global marker editing in BlueMap.
- [PrivateLocation](./PrivateLocation) restricts the player feed to the current user.
- [PrivateMaps](./PrivateMaps) permission-gates individual maps.
