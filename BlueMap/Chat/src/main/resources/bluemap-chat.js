function getAuthStatus() {
    return new Promise((res, err) => {
        let timeout;

        const acquireInterval = setInterval(() => {
            if (window.bluemapAuth) {
                clearInterval(acquireInterval);
                if (timeout) clearTimeout(timeout);
                res(window.bluemapAuth);
            }
        }, 10);

        timeout = setTimeout(() => {
            clearInterval(acquireInterval);
            err()
        }, 2000);
    });
}

const nbps = String.fromCharCode(160);

const chatSvg = `<svg viewBox="0 0 30 30"><path d="m 22.988,6.045 c 1.616,0 2.938,0.94 2.938,2.09 v 10.927 c 0,1.15 -1.322,2.09 -2.938,2.09 H 8.55 c -1.616,0 -2.938,-0.94 -2.938,-2.09 V 8.135 c 0,-1.15 1.322,-2.09 2.938,-2.09 z"/><path d="M 12.757,20.93 4.074,23.956 5.795,14.923 Z"/></svg>`;

void async function() {
    const auth = await getAuthStatus().catch(() => {
        console.warn("[Chat/warn] Could not acquire auth status, sending not enabled");
        return null;
    });
    console.log("[Chat/info] Web chat loaded!");

    const root = document.createElement("div");
    root.id = "chat-root";
    root.style.display = "none";
    const chatMessages = document.createElement("div");
    chatMessages.id = "chat-messages";
    root.appendChild(chatMessages);
    const chatInput = document.createElement("input");
    chatInput.id = "chat-input";
    chatInput.placeholder = "Enter your message...";
    chatInput.maxLength = 256;
    chatInput.enterKeyHint = "enter";
    root.appendChild(chatInput);
    document.body.insertBefore(root, document.getElementById("app"));

    if (!auth || !auth.loggedIn) {
        chatInput.disabled = true;
        chatInput.placeholder = "Log in to send messages";
    }

    const toggleBtnControl = document.createElement("div");
    toggleBtnControl.className = "svg-button chat-toggle";
    toggleBtnControl.innerHTML = chatSvg;
    const toggleBtnZoom = toggleBtnControl.cloneNode(true);
    const spacer = document.createElement("div");
    spacer.className = "space thin-hide";

    const cb = document.querySelector(".control-bar");
    const cbReference = [...cb.children].find(el => el.className === "space thin-hide greedy");
    cbReference.parentNode.insertBefore(spacer, cbReference);
    cbReference.parentNode.insertBefore(toggleBtnControl, cbReference);

    const zb = document.querySelector("#zoom-buttons");
    zb.insertBefore(toggleBtnZoom, zb.children[0]);

    let chatIsOpen = false;
    function toggleChat() {
        if (chatIsOpen) {
            toggleBtnControl.classList.remove("active");
            toggleBtnZoom.classList.remove("active");
            root.style.display = "none";
        } else {
            toggleBtnControl.classList.add("active");
            toggleBtnZoom.classList.add("active");
            root.style.display = "";
        }
        chatIsOpen = !chatIsOpen;
    }

    toggleBtnControl.addEventListener("click", toggleChat);
    toggleBtnZoom.addEventListener("click", toggleChat);

    chatInput.addEventListener("keydown", (e) => {
        e.stopPropagation();
        if (e.key === "Enter") {
            fetch("./addons/chat/send", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    message: chatInput.value
                })
            });
            chatInput.value = "";
        }
    });

    function addMessage(str, className) {
        const message = document.createElement("div");
        message.innerText = str;
        if (className) {
            message.className = className;
        }
        chatMessages.insertBefore(message, chatMessages.firstChild);
        if (chatMessages.children.length > 100) {
            chatMessages.removeChild(chatMessages.lastChild);
        }
    }

    const e = new EventSource("./addons/chat/stream");
    e.onerror = () => {
        console.log("[Chat/info] Chat requires login to receive messages");
        chatInput.placeholder = "Log in to receive messages";
        if (chatIsOpen) toggleChat()
    };
    e.onmessage = (event) => {
        const data = JSON.parse(event.data);
        switch (data.type) {
            case "settings": {
                if (data.readOnly) {
                    console.log("[Chat/info] Chat is in read-only mode");
                    root.removeChild(chatInput);
                    chatMessages.style.height = "100%";
                }
                if (!chatIsOpen) toggleChat()
                break;
            }
            case "chat": {
                addMessage(data.username + ":" + nbps + data.message);
                break;
            }
            case "webchat": {
                addMessage("[web]" + nbps + data.username + ":" + nbps + data.message);
                break;
            }
            case "join": {
                addMessage(data.username + " joined", "joinquit");
                break;
            }
            case "quit": {
                addMessage(data.username + " left", "joinquit");
                break;
            }
            case "death": {
                addMessage(data.message, "death");
                break;
            }
            default:
                console.warn("[Chat/warn] Unknown event type", data);
        }
    }
}();
