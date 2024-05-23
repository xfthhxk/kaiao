this.kaiao = {};

kaiao.genUUID = function() {
  if ("crypto" in window) {
    return window.crypto.randomUUID();
  } else {
    console.error("crypto module is not available.");
    throw new Error("crypto module is not available.");
  }
};

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


kaiao.projectId = function() {
  return this.config()["project-id"];
};

kaiao.send = function(data) {
  let endpoint = this.config()["endpoint"];
  fetch(endpoint, {method: "POST",
                   headers: {"content-type": "application/json"},
                   redirect: "follow",
                   body: JSON.stringify(data)});
};

kaiao.sessionStarted = function(data, user = null) {
  if (null == data) {
    data = {};
  }

  var sessionId = null;

  if ("kaiao/session-id" in data) {
    sessionId = data["kaiao/session-id"];
    delete data["kaiao/session-id"];
  } else {
    sessionId = this.genUUID();
  }

  let now = new Date().getTime();

  this.writeLS("kaiao/session", {"id": sessionId, "started-at": now});

  let cfg = this.config();

  let defaultData = {
    "location/hostname": window.location.hostname,
    "navigator/language": navigator.language,
    "screen/height": window.screen.height,
    "screen/width": window.screen.width
  };

  data = this.merge(defaultData, data);

  let session = {
    "id": sessionId,
    "project-id": cfg["project-id"],
    "project-version-id": cfg["project-version-id"],
    "data": data,
    "started-at": now
  };


  if ("tags" in data) {
    session["tags"] = data.tags;
  }

  let metadata = {
    "op": "kaiao.op/session-started"
  };

  if (null != user) {
    metadata.user = user;
    session["user-id"] = user.id;
  }

  this.send({"metadata": metadata,
             "data": session});
};


kaiao.identify = function(user) {
  user["project-id"] = this.projectId();
  this.send({metadata: {"op": "kaiao.op/identify"},
             data: {"session-id": this.sessionId(),
                    "user": user}});
};

kaiao.sessionEnded = function() {
  let id = this.sessionId();

  if(null == id) {
    console.log("no existing session");
    return;
  }

  let data = {
    "id": id,
    "ended-at": new Date().getTime()
  };

  this.send({metadata: {"op": "kaiao.op/session-ended",
                        "project-id": this.projectId()},
             data: data});
  localStorage.removeItem("kaiao/session");
};


kaiao.track = function(eventName, data, tags) {
  if (null == eventName) {
    throw Error("event name is required");
  }

  if (null == data) {
    data = {};
  }

  var eventId = null;

  if ("kaiao/event-id" in data) {
    eventId = data["kaiao/event-id"];
    delete data["kaiao/event-id"];
  } else {
    eventId = this.genUUID();
  }

  let referrerData = {};
  if (null!=document.referrer && document.referrer.length > 0) {
    let url = new URL(document.referrer);
    referrerData = {
      "referrer/path": url.pathname,
      "referrer/hostname": url.hostname
    };

    if ("" != url.search) {
      referrerData["referrer/query"] = url.search;
    }
  }

  let pageData = {
    "page/title": document.title,
    "page/path": window.location.pathname
  };

  if ("" != window.location.search) {
    pageData["page/query"] = window.location.search;
  }

  let cfg = this.config();

  let event = {
    "id": window.crypto.randomUUID(),
    "project-id": cfg["project-id"],
    "session-id": this.sessionId(),
    "name": eventName,
    "occurred-at": new Date().getTime(),
    "data": this.merge(referrerData, pageData, data),
  };

  if (null != tags) {
    event["tags"] = tags;
  }

  this.send({metadata: {"op": "kaiao.op/events"},
             data: {events: [event]}});
};

window.kaiao = kaiao;
