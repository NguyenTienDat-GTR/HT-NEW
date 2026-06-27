(function() {
  const SWAGGER_ACCESS_TOKEN_KEY = "swagger.accessToken";
  const AUTH_LOGIN_PATH = "/api/auth/login";
  const AUTH_REFRESH_PATH = "/api/auth/refresh";

  function readToken() {
    try {
      return window.localStorage.getItem(SWAGGER_ACCESS_TOKEN_KEY);
    } catch (error) {
      return null;
    }
  }

  function storeToken(token) {
    if (!token) {
      return;
    }
    try {
      window.localStorage.setItem(SWAGGER_ACCESS_TOKEN_KEY, token);
    } catch (error) {
      console.warn("Unable to store Swagger access token", error);
    }
  }

  function clearToken() {
    try {
      window.localStorage.removeItem(SWAGGER_ACCESS_TOKEN_KEY);
    } catch (error) {
      console.warn("Unable to clear Swagger access token", error);
    }
  }

  function decodeTokenPayload(token) {
    if (!token) {
      return null;
    }

    try {
      const parts = token.split(".");
      if (parts.length < 2) {
        return null;
      }

      const normalized = parts[1].replace(/-/g, "+").replace(/_/g, "/");
      const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
      return JSON.parse(window.atob(padded));
    } catch (error) {
      console.warn("Unable to decode Swagger access token", error);
      return null;
    }
  }

  function isTokenExpired(token) {
    const payload = decodeTokenPayload(token);
    if (!payload || !payload.exp) {
      return false;
    }

    return (Date.now() + 5000) >= payload.exp * 1000;
  }

  function getValidStoredToken() {
    const token = readToken();
    if (!token) {
      return null;
    }

    if (isTokenExpired(token)) {
      clearToken();
      return null;
    }

    return token;
  }

  function isAuthRequest(url) {
    return !!url && (url.indexOf(AUTH_LOGIN_PATH) >= 0 || url.indexOf(AUTH_REFRESH_PATH) >= 0);
  }

  function captureTokenFromResponse(response) {
    if (!response || !response.url) {
      return;
    }

    if (!isAuthRequest(response.url)) {
      return;
    }

    try {
      const payload = typeof response.data === "string" ? JSON.parse(response.data) : response.data;
      const accessToken = payload && payload.data && payload.data.accessToken;
      storeToken(accessToken);
    } catch (error) {
      console.warn("Unable to capture access token from auth response", error);
    }
  }

  function patchSwaggerUi() {
    if (!window.ui || typeof window.ui.getConfigs !== "function") {
      window.setTimeout(patchSwaggerUi, 300);
      return;
    }

    const configs = window.ui.getConfigs();
    const originalRequestInterceptor = configs.requestInterceptor;
    const originalResponseInterceptor = configs.responseInterceptor;

    configs.requestInterceptor = function(request) {
      if (request && request.url && isAuthRequest(request.url)) {
        clearToken();
      }

      const token = getValidStoredToken();
      if (token && request && request.url && !isAuthRequest(request.url)) {
        request.headers = request.headers || {};
        if (!request.headers.Authorization) {
          request.headers.Authorization = "Bearer " + token;
        }
      }

      return originalRequestInterceptor ? originalRequestInterceptor(request) : request;
    };

    configs.responseInterceptor = function(response) {
      captureTokenFromResponse(response);
      if (response && (response.status === 401 || response.status === 403)) {
        clearToken();
      }

      return originalResponseInterceptor ? originalResponseInterceptor(response) : response;
    };
  }

  patchSwaggerUi();
})();
