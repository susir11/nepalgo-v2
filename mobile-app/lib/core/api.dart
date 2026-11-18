import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'env.dart';

// Persists our NepalGo tokens in the platform keystore (Keychain / Android
// Keystore via flutter_secure_storage), never in plain SharedPreferences.
class TokenStore {
  static const _s = FlutterSecureStorage();
  static const _access = 'ng_access', _refresh = 'ng_refresh';
  String? access;

  Future<void> load() async => access = await _s.read(key: _access);
  Future<String?> refreshToken() => _s.read(key: _refresh);

  Future<void> save(String access, String refresh) async {
    this.access = access;
    await _s.write(key: _access, value: access);
    await _s.write(key: _refresh, value: refresh);
  }

  Future<void> clear() async {
    access = null;
    await _s.delete(key: _access);
    await _s.delete(key: _refresh);
  }
}

/// A server error in our ProblemDetail format: stable `code` + human `detail`.
class ApiException implements Exception {
  final int? status;
  final String? code;
  final String message;
  final Map<String, dynamic>? body;
  ApiException(this.status, this.code, this.message, [this.body]);
  @override
  String toString() => message;

  static ApiException from(Object e) {
    if (e is ApiException) return e;
    if (e is DioException) {
      final data = e.response?.data;
      if (data is Map) {
        final m = Map<String, dynamic>.from(data);
        return ApiException(e.response?.statusCode, m['code'] as String?,
            (m['detail'] ?? m['title'] ?? m['message'] ?? 'Something went wrong').toString(), m);
      }
      if (e.type == DioExceptionType.connectionError || e.type == DioExceptionType.connectionTimeout) {
        return ApiException(null, 'OFFLINE', 'Can\'t reach NepalGo. Check your connection and try again.');
      }
      return ApiException(e.response?.statusCode, null, 'Something went wrong (${e.response?.statusCode ?? 'network'}).');
    }
    return ApiException(null, null, e.toString());
  }
}

/// Dio client for the gateway. Adds the bearer token; on a 401 refreshes ONCE
/// (single-flight, so 5 parallel requests don't trigger 5 refreshes) and retries.
class Api {
  final TokenStore tokens;
  final Dio dio;
  final void Function() onSessionExpired;
  Completer<bool>? _refreshing;

  Api(this.tokens, {required this.onSessionExpired})
      : dio = Dio(BaseOptions(baseUrl: Env.apiBase, connectTimeout: const Duration(seconds: 15), receiveTimeout: const Duration(seconds: 30))) {
    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (o, h) {
        if (tokens.access != null && o.extra['noAuth'] != true) o.headers['Authorization'] = 'Bearer ${tokens.access}';
        h.next(o);
      },
      onError: (e, h) async {
        final req = e.requestOptions;
        if (e.response?.statusCode == 401 && req.extra['noAuth'] != true && req.extra['retried'] != true && await _refresh()) {
          req.extra['retried'] = true;
          req.headers['Authorization'] = 'Bearer ${tokens.access}';
          try {
            return h.resolve(await dio.fetch(req));
          } on DioException catch (e2) {
            return h.next(e2);
          }
        }
        h.next(e);
      },
    ));
  }

  Future<bool> _refresh() async {
    if (_refreshing != null) return _refreshing!.future;
    final c = _refreshing = Completer<bool>();
    try {
      final rt = await tokens.refreshToken();
      if (rt == null) throw StateError('no refresh token');
      final r = await dio.post('/api/v1/auth/refresh', data: {'refreshToken': rt}, options: Options(extra: {'noAuth': true}));
      await tokens.save(r.data['accessToken'], r.data['refreshToken'] ?? rt);
      c.complete(true);
    } catch (_) {
      await tokens.clear();
      onSessionExpired();
      c.complete(false);
    } finally {
      _refreshing = null;
    }
    return c.future;
  }

  Future<T> get<T>(String path, {Map<String, dynamic>? query}) => _wrap(() => dio.get(path, queryParameters: query));
  Future<T> post<T>(String path, [Object? body]) => _wrap(() => dio.post(path, data: body ?? {}));
  Future<T> delete<T>(String path, [Object? body]) => _wrap(() => dio.delete(path, data: body));
  Future<T> postNoAuth<T>(String path, Object body) => _wrap(() => dio.post(path, data: body, options: Options(extra: {'noAuth': true})));

  Future<T> _wrap<T>(Future<Response> Function() call) async {
    try {
      return (await call()).data as T;
    } catch (e) {
      throw ApiException.from(e);
    }
  }
}
