void async function() {
    console.log("[Auth/info] Authentication integration loaded!");

    const logOutTemplate = document.createElement("template");
    logOutTemplate.innerHTML = `
    <a style="text-decoration: none" href="{{auth-path}}logout?redirect=${encodeURIComponent(window.location.pathname)}">
        <div class="simple-button">
            <div class="label">Logout</div>
        </div>
    </a>
    `.trim();
    const logOutButton = logOutTemplate.content.firstChild;

    const logOutAllTemplate = document.createElement("template");
    logOutAllTemplate.innerHTML = `
    <a style="text-decoration: none" href="{{auth-path}}logout/all?redirect=${encodeURIComponent(window.location.pathname)}">
        <div class="simple-button">
            <div class="label">Logout All Browsers</div>
        </div>
    </a>
    `.trim();
    const logOutAllButton = logOutAllTemplate.content.firstChild;

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
                src="./addons/integration/playerheads/${window.bluemap.mapViewer.data.map.id}/${uuid}"
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
                userElement.children.item(0).src = `./addons/integration/playerheads/${window.bluemap.mapViewer.data.map.id}/${uuid}`
                buttonList.insertBefore(userElement, buttonList.childNodes[0]);
            } else {
                buttonList.appendChild(logInButton);
            }
        }
    }, 10);
}();
