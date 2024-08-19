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
const expandSvg = `<svg viewBox="0 0 24 24"><g fill="none" fill-rule="evenodd"><path d="M24 0v24H0V0zM12.593 23.258l-.011.002l-.071.035l-.02.004l-.014-.004l-.071-.035q-.016-.005-.024.005l-.004.01l-.017.428l.005.02l.01.013l.104.074l.015.004l.012-.004l.104-.074l.012-.016l.004-.017l-.017-.427q-.004-.016-.017-.018m.265-.113l-.013.002l-.185.093l-.01.01l-.003.011l.018.43l.005.012l.008.007l.201.093q.019.005.029-.008l.004-.014l-.034-.614q-.005-.019-.02-.022m-.715.002a.02.02 0 0 0-.027.006l-.006.014l-.034.614q.001.018.017.024l.015-.002l.201-.093l.01-.008l.004-.011l.017-.43l-.003-.012l-.01-.01z"/><path fill="currentColor" d="M10.94 7.94a1.5 1.5 0 0 1 2.12 0l5.658 5.656a1.5 1.5 0 1 1-2.122 2.121L12 11.122l-4.596 4.596a1.5 1.5 0 1 1-2.122-2.12z"/></g></svg>`
const closeSvg = `<svg width="128" height="128" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m7 7l10 10M7 17L17 7"/></svg>`

void async function() {
    const auth = await getAuthStatus().catch(() => {
        console.warn("[Chat/warn] Could not acquire auth status, sending not enabled");
        return null;
    });
    console.log("[Chat/info] Web chat loaded!");

    const root = document.createElement("div");
    root.id = "chat-root";
    root.style.display = "none";
    const messagesPopup = document.createElement("div");
    messagesPopup.id = "chat-messages-popup"
    const chatMessages = document.createElement("div");
    chatMessages.id = "chat-messages";
    chatMessages.classList.add("messages-div-hidden");

    const toggleChatViewControl = document.createElement("div");
    toggleChatViewControl.id = "toggle-chat-view-control";
    toggleChatViewControl.classList.add("svg-button")
    toggleChatViewControl.innerHTML = expandSvg;

    root.classList.add("no-messages");

    root.appendChild(messagesPopup);
    root.appendChild(chatMessages);
    root.appendChild(toggleChatViewControl);

    const chatInputWrapper = document.createElement("div");
    chatInputWrapper.id = "chat-input-wrapper";
    const chatInput = document.createElement("input");
    chatInput.id = "chat-input";
    chatInput.placeholder = "Enter your message...";
    chatInput.maxLength = 256;
    chatInput.enterKeyHint = "enter";
    chatInputWrapper.appendChild(chatInput);
    root.appendChild(chatInputWrapper);
    document.body.insertBefore(root, document.getElementById("app"));

    function setupChatLoggedOut() {
        chatInput.disabled = true;
        chatInputWrapper.classList.add("chat-logged-out");

        let authPath = "{{auth-path}}"

        if (authPath === "{{"+"auth-path"+"}}") {
            chatInput.placeholder = "Log in to send messages";
            chatInputWrapper.onclick = () => {
                console.log("[Chat/info] Log in to send messages");
                window.location.href = `${authPath}login?redirect=${encodeURIComponent(window.location.pathname)}`;
            }
        }
        else {
            chatInputWrapper.classList.add("read-only");
            chatInput.placeholder = "";
        }
    }

    if (!auth || !auth.loggedIn) setupChatLoggedOut();

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

    let chatExpanded = false;
    function toggleExpanded() {
        if (chatExpanded) {
            // If viewing the div just for popups
            chatMessages.classList.add("messages-div-hidden");
            messagesPopup.classList.remove("messages-div-hidden");
            toggleChatViewControl.innerHTML = expandSvg;
            messagesPopup.innerHTML = "";
            root.classList.add("no-messages");
            chatExpanded = false;
        }
        else {
            // If viewing the expanded version
            chatMessages.classList.remove("messages-div-hidden");
            messagesPopup.classList.add("messages-div-hidden");
            toggleChatViewControl.innerHTML = closeSvg;
            root.classList.remove("no-messages");

            if (chatMessages.children.length === 0) {
                root.classList.remove("no-messages");
                const message = document.createElement("div");
                message.classList.add("empty-message");
                message.innerText = "No messages yet";
                chatMessages.insertBefore(message, chatMessages.firstChild);
            }
            chatExpanded = true;
        }
    }

    toggleBtnControl.addEventListener("click", toggleChat);
    toggleBtnZoom.addEventListener("click", toggleChat);

    toggleChatViewControl.addEventListener("click", toggleExpanded);

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

    addEventListener("keydown", (e) => {
        if (e.key === "t") {
            toggleChat();
            if (!chatExpanded) {
                toggleExpanded();
                chatInput.focus();
            }
        }
    });

    let mmc = "{{max-message-count}}"

    function addMessage(str, className) {
        chatMessages.querySelectorAll(".empty-message").forEach(el => el.remove());
        const message = document.createElement("div");
        message.innerText = str;
        if (className) {
            message.className = className;
        }
        chatMessages.insertBefore(message, chatMessages.firstChild);
        let pc = message.cloneNode(true);
        messagesPopup.insertBefore(pc, messagesPopup.firstChild);
        setTimeout(() => {
            pc.classList.add("fade-out");
            setTimeout(() => {
                messagesPopup.removeChild(pc);

                if (messagesPopup.children.length === 0 && !chatExpanded) {
                    root.classList.add("no-messages");
                }
            }, 500)
        }, 5000)
        if (chatMessages.children.length > mmc.toString()) {
            chatMessages.removeChild(chatMessages.lastChild);
        }
        root.classList.remove("no-messages");
    }

    let wcp = "{{web-chat-prefix}}";

    const e = new EventSource("./addons/chat/stream");
    e.onerror = () => {
        setupChatLoggedOut();
        if (chatIsOpen) toggleChat();
    };
    e.onmessage = (event) => {
        const data = JSON.parse(event.data);
        switch (data.type) {
            case "settings": {
                if (data.readOnly) {
                    console.log("[Chat/info] Chat is in read-only mode");
                    chatInputWrapper.classList.add("read-only");
                    chatInput.placeholder = "";
                    chatInput.disabled = true;
                }
                if (!chatIsOpen) toggleChat()
                break;
            }
            case "chat": {
                addMessage(data.username + ":" + nbps + data.message);
                break;
            }
            case "webchat": {
                addMessage(wcp + nbps + data.username + ":" + nbps + data.message);
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
