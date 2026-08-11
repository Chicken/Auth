import { render } from "preact";
import { App } from "./components/App";
import { BlueMapBridge, waitForAuthentication, waitForBlueMap } from "./bluemap";
import "./styles.css";

void (async () => {
  try {
    const bluemap = await waitForBlueMap();
    const integrationAvailable = await waitForAuthentication();
    const host = document.createElement("div");
    host.id = "marker-editor-host";
    document.body.insertBefore(host, document.getElementById("app"));
    render(<App bridge={new BlueMapBridge(bluemap)} integrationAvailable={integrationAvailable} />, host);
    console.info("[MarkerEditor/info] Marker editor loaded");
  } catch (error) {
    console.error("[MarkerEditor/error] Failed to load marker editor", error);
  }
})();
