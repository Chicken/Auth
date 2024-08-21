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

package codes.antti.auth.bluemap.privatelocation;

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

    public static void createPlayerHeadIfNotExists(
            final @NotNull BlueMapAPI blueMapAPI,
            final @NotNull BlueMapMap map,
            final @NotNull UUID playerUUID
    ) throws IOException {
        final String assetName = "playerheads/" + playerUUID + ".png";
        if (map.getAssetStorage().assetExists(assetName)) return;
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
