void async function() {
    console.log("[Auth/info] Authentication integration loaded!");

    function makePostButton(label, endpoint) {
        const el = document.createElement("div");
        el.className = "simple-button";
        el.style.cursor = "pointer";
        el.innerHTML = `<div class="label">${label}</div>`;
        el.addEventListener("click", () => {
            fetch(`{{auth-path}}${endpoint}?redirect=${encodeURIComponent(window.location.pathname)}`, { method: "POST" })
                .then(() => { window.location.href = window.location.pathname; });
        });
        return el;
    }
    const logOutButton = makePostButton("Logout", "logout");
    const logOutAllButton = makePostButton("Logout All Browsers", "logout/all");

    const logInTemplate = document.createElement("template");
    logInTemplate.innerHTML = `
    <a style="text-decoration: none" href="{{auth-path}}login?redirect=${encodeURIComponent(window.location.pathname)}">
        <div class="simple-button">
            <div class="label">Log in</div>
        </div>
    </a>
    `.trim();
    const logInButton = logInTemplate.content.firstChild;

    const {
        uuid,
        username
    } = await fetch("./addons/integration/whoami").then(r => r.json());

    let userElement;
    if (uuid) {
        const userTemplate = document.createElement("template");
        userTemplate.innerHTML = `
        <div style="display: flex; flex-direction: row; align-items: center; padding: .1em .5em;">
            <img
                src="./addons/integration/playerhead/${window.bluemap.mapViewer.data.map.id}"
                style="width: 28px; height: 28px; margin-right: 8px; border-radius: 2px;"
                alt="Your playerhead" />
            ${username}
        </div>
        `.trim();
        userElement = userTemplate.content.firstChild;
        window.bluemapAuth = { loggedIn: true, uuid, username };
    } else {
        window.bluemapAuth = { loggedIn: false };
    }

    setInterval(() => {
        const buttonList = document.querySelector(".side-menu .content")?.children.item(0);
        if (buttonList && Array.from(buttonList.children).every(el => el.tagName === "HR" || el.className === "simple-button")) {
            if (uuid) {
                buttonList.appendChild(logOutButton);
                buttonList.appendChild(logOutAllButton);
                userElement.children.item(0).src = `./addons/integration/playerhead/${window.bluemap.mapViewer.data.map.id}`
                buttonList.insertBefore(userElement, buttonList.childNodes[0]);
            } else {
                buttonList.appendChild(logInButton);
            }
        }
    }, 10);
}();
