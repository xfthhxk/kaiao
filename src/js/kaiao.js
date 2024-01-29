this.kaiao = {};

kaiao.merge = function(a,b,c) {
  return {...a, ...b, ...c};
};

kaiao.writeLS = function(theKey, m) {
  localStorage.setItem(theKey, JSON.stringify(m));
};

kaiao.readLS = function(theKey) {
  let s = localStorage.getItem(theKey);
  if (null!=s) {
    return JSON.parse(s);
  }
  return {};
};

kaiao.init = function(m) {
  this.writeLS("kaiao/config", m);
};

kaiao.config = function() {
  return this.readLS("kaiao/config");
};

kaiao.session = function() {
  return this.readLS("kaiao/session");
};

kaiao.sessionId = function() {
  return this.session()["id"];
};

kaiao.send = function(data) {
  let endpoint = this.config()["endpoint"];
  fetch(endpoint, {method: "POST",
                   headers: {"content-type": "application/json"},
                   redirect: "follow",
                   body: JSON.stringify(data)});
};

kaiao.identify = function(user) {
  user["project-id"] = this.projectId();
  this.send({metadata: {op: "kaiao.op/identify"},
             data: {user: user}});
};

kaiao.sessionStarted = function(data) {
  let sessionId = window.crypto.randomUUID();
  let now = new Date().getTime();
  this.writeLS("kaiao/session", {"id": sessionId, "created-at": now});
  let cfg = this.config();
  let defaults = {
    "id": sessionId,
    "project-id": cfg["project-id"],
    "project-version-id": cfg["project-version-id"],
    "hostname": window.location.hostname,
    "language": navigator.language,
    "screen-height": window.screen.height,
    "screen-width": window.screen.width,
    "created-at": now
  };
  data = this.merge(defaults, data);
  this.send({metadata: {op: "kaiao.op/session"},
             data: data});
};


kaiao.track = function(data) {
  if ("string" === typeof data) {
    data = {"name": data};
  }

  let referrerInfo = {};
  if (null!=document.referrer && document.referrer.length > 0) {
    let url = new URL(document.referrer);
    referrerInfo = {
      "referrer-path": url.pathname,
      "referrer-query": url.search,
      "referrer-host": url.host
    };
  }

  let cfg = this.config();
  let defaults = {
    "id": window.crypto.randomUUID(),
    "project-id": cfg["project-id"],
    "session-id": this.sessionId(),
    "url-path": window.location.pathname,
    "url-query": window.location.search,
    "page-title": document.title,
    "created-at": new Date().getTime()
  };
  data = this.merge(defaults,referrerInfo, data);
  if (null == data["name"]) {
    throw Error("event name is required");
  }
  this.send({metadata: {op: "kaiao.op/events"},
             data: [data]});
};

window.kaiao = kaiao;
