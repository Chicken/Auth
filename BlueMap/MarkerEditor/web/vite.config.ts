import preact from "@preact/preset-vite";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [preact()],
  build: {
    lib: {
      entry: "src/main.tsx",
      name: "BlueMapMarkerEditor",
      formats: ["iife"],
      fileName: () => "marker-editor.js",
      cssFileName: "marker-editor",
    },
    rollupOptions: {
      output: {
        inlineDynamicImports: true,
      },
    },
  },
});
