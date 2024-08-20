/*
 * This file is part of BMUtils, licensed under the MPL2 License (MPL).
 * Please keep tabs on https://github.com/TechnicJelle/BMUtils for updates.
 *
 * Copyright (c) TechnicJelle <https://technicjelle.com>
 * Copyright (c) contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package codes.antti.auth.bluemap.integration;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.plugin.SkinProvider;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import java.util.UUID;

/**
 * Utility functions for BlueMap skins and playerheads
 */
public class BMSkin {
    private BMSkin() {
        throw new IllegalStateException("Utility class");
    }

    private static final String FALLBACK_ICON = "assets/steve.png";

    /**
     * Gets the URL to a player head icon for a specific map.<br>
     * If the icon doesn't exist yet, it will be created.
     *
     * @param blueMapAPI The BlueMapAPI instance
     * @param playerUUID The player to get the head of
     * @param blueMapMap The map to get the head for (each map has its own playerheads folder)
     * @return The URL to the player head, relative to BlueMap's web root,<br>
     * or a Steve head if the head couldn't be found
     */
    public static String getPlayerHeadIconAddress(
            final @NotNull BlueMapAPI blueMapAPI,
            final @NotNull UUID playerUUID,
            final @NotNull BlueMapMap blueMapMap
    ) {
        final String assetName = "playerheads/" + playerUUID + ".png";

        try {
            if (!blueMapMap.getAssetStorage().assetExists(assetName)) {
                createPlayerHead(blueMapAPI, playerUUID, assetName, blueMapMap);
            }
        } catch (IOException e) {
            return FALLBACK_ICON;
        }

        return blueMapMap.getAssetStorage().getAssetUrl(assetName);
    }

    /**
     * For when BlueMap doesn't have an icon for this player yet, so we need to make it create one.
     */
    private static void createPlayerHead(
            final @NotNull BlueMapAPI blueMapAPI,
            final @NotNull UUID playerUUID,
            final @NotNull String assetName,
            final @NotNull BlueMapMap map
    ) throws IOException {
        final SkinProvider skinProvider = blueMapAPI.getPlugin().getSkinProvider();
        try {
            final Optional<BufferedImage> oImgSkin = skinProvider.load(playerUUID);
            if (oImgSkin.isEmpty()) {
                throw new IOException(playerUUID + " doesn't have a skin");
            }

            try (OutputStream out = map.getAssetStorage().writeAsset(assetName)) {
                final BufferedImage head = blueMapAPI.getPlugin().getPlayerMarkerIconFactory()
                        .apply(playerUUID, oImgSkin.get());
                ImageIO.write(head, "png", out);
            } catch (IOException e) {
                throw new IOException("Failed to write " + playerUUID + "'s head to asset-storage", e);
            }
        } catch (IOException e) {
            throw new IOException("Failed to load skin for player " + playerUUID, e);
        }
    }
}
