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

void async function() {
    const auth = await getAuthStatus().catch(() => {
        console.warn("[Chat/warn] Could not acquire auth status, sending not enabled");
        return null;
    });
    console.log("[Chat/info] Web chat loaded!");

    const root = document.createElement("div");
    root.id = "chat-root";
    const chatMessages = document.createElement("div");
    chatMessages.id = "chat-messages";
    root.appendChild(chatMessages);
    const chatInput = document.createElement("input");
    chatInput.id = "chat-input";
    chatInput.placeholder = "Enter your message...";
    chatInput.maxLength = 256;
    root.appendChild(chatInput);

    if (!auth || !auth.loggedIn) {
        chatInput.disabled = true;
        chatInput.placeholder = "Log in to send messages";
    }

    chatInput.addEventListener("keydown", (e) => {
        e.stopPropagation();
        if (e.key === "Enter") {
            fetch("/addons/chat/send", {
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

    const e = new EventSource("/addons/chat/stream");
    e.onerror = () => {
        console.log("[Chat/info] Chat requires login to send messages");
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
                document.body.insertBefore(root, document.getElementById("app"));
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
