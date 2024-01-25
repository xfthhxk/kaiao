kaiao = {};

kaiao.merge = function(a,b) {
  return {...a, ...b};
};

kaiao.init = function(m) {
  localStrorage.setItem("kaiao/config", JSON.stringify(m));
};

kaiao.reset() {
  localStorage.setItem("kaiao/session-id", window.crypto.randomUUID());
}

kaiao.sessionId = function() {
  return localStorage.getItem("kaiao/session-id");
};

kaiao.projectVersionId = function() {
  return localStorage.getItem("kaiao/project-version-id");
};

kaiao.config = function() {
  let s = localStorage.getItem("kaiao/config");
  if (null!=s) {
    return JSON.parse(s);
  }
  return null;
};

kaiao.send = function(data) {
  let config = kaiao.config();
  let endpoint = config["kaiao/endpoint"];
  fetch(endpoint, {method: "POST",
                   headers: {"content-type": "application/json"},
                   redirect: "follow",
                   body: JSON.stringify(data)});
};

kaiao.identify = function(user) {
  let config = kaiao.config();
  user["project-id"] = config["kaiao/project-id"];
  kaiao.send({metadata: {op: "kaiao.op/identify"},
        data: {user: user}});
};

kaiao.sessionStarted = function(data) {
  let sessionId = window.crypto.randomUUID();
  localStorage.setItem("kaiao/session-id", sessionId);
  let config = kaiao.config();
  let defaults = {
    "session-id": sessionId,
    "project-id": config["kaiao/project-id"],
    "project-version-id": config["kaiao/project-version-id"],
    "hostname": window.location.hostname,
    "language": navigator.language,
    "screen-height": window.screen.height,
    "screen-width": window.screen.width,
    "created-at": new Date().toISOString()
  };
  data = kaiao.merge(defaults, data);
  kaiao.send({metadata: {op: "kaiao.op/session"},
        data: data});
};


kaiao.track = function(data) {
  if ("string" === typeof data) {
    data = {key: data};
  }
  let url = new URL(document.referrer);
  let defaults = {
    "event-id": window.crypto.randomUUID(),
    "session-id": kaiao.sessionId(),
    "url-path": window.location.pathname,
    "url-query": window.location.search,
    "referrer-path": url.pathname,
    "referrer-query": url.search,
    "referrer-domain": url.host,
    "page-title": document.title,
    "created-at": new Date().toISOString()
  };
  let data = kaiao.merge(defaults, data);
  kaiao.send({metadata: {op: "kaiao.op/event"},
        data: data});
};

window.kaiao = kaiao;
