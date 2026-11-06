import angular from 'angular';

angular.module('wxn.core').factory('ApiClientService', [
  '$http',
  'environment',
  function ($http, environment) {
    function get(path, params, headers) {
      return $http
        .get(environment.apiBaseUrl + '/api' + path, { params, headers, timeout: 30000 })
        .then((r) => r.data);
    }
    function allPages(path, params = {}) {
      const result = [];
      function load(page) {
        return get(path, { ...params, page, size: 200 }).then((rows) => {
          result.push(...rows);
          return rows.length === 200 ? load(page + 1) : result;
        });
      }
      return load(0);
    }
    return {
      get,
      allPages,
      delete(path) {
        return $http.delete(environment.apiBaseUrl + '/api' + path, { timeout: 30000 }).then((r) => r.data);
      },
      put(path, body) {
        return $http
          .put(environment.apiBaseUrl + '/api' + path, body, { timeout: 30000 })
          .then((r) => r.data);
      },
      patch(path, body) {
        return $http
          .patch(environment.apiBaseUrl + '/api' + path, body, { timeout: 30000 })
          .then((r) => r.data);
      },
      post(path, body, headers) {
        return $http
          .post(environment.apiBaseUrl + '/api' + path, body, { headers, timeout: 30000 })
          .then((r) => r.data);
      },
    };
  },
]);
