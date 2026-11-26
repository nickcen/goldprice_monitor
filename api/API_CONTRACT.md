# API v1 contract

Base path: `/api/v1`. JSON request and response keys use camelCase. All authenticated requests send `Authorization: Bearer <token>`. Errors use `{ "code": "...", "message": "..." }`; 400 invalid parameters, 401 absent/invalid/expired account or token, 403 wrong role, 404 missing resource, 409 duplicate phone or deletion conflict, 503 price source failure. List responses use `{items,page,pageSize,total,totalPages}` with fixed page size 10 and one-based `page`.

## Authentication

| Method | Path | Body | Response |
| --- | --- | --- | --- |
| POST | `/auth/register` | `{phone,password}` | `{id,phone}` |
| POST | `/auth/login` | `{phone,password}` | `{token,expiresAt,role,userId}` |
| POST | `/admin/auth/login` | `{username,password}` | same |
| GET | `/auth/session` | none | `{userId,role,expiresAt}` |
| POST | `/auth/refresh` | none | new token response; old token revoked |
| POST | `/auth/logout` | none | `{success:true}`; session revoked |

Phone input accepts E.164 or a mainland China 11-digit mobile number, stored as `+86...`. Password length is 8–72 characters. Tokens are HS256 JWTs valid for two hours with a server-side session ID. Refresh replaces the current session; logout revokes it. Password changes revoke all the user's sessions. User tokens cannot access `/admin/**` except the admin login route; admin tokens cannot access `/me/**` or the price endpoint. All other protected endpoints check current account status and UTC date validity on every request.

## User endpoints

| Method | Path | Body / filter | Response |
| --- | --- | --- | --- |
| GET | `/me/config` | none | `{level,monitorIntervalSeconds,minPrice,maxPrice,goldPriceUrl,unit,currency,precision}` |
| PATCH | `/me/config` | any of `minPrice`, `maxPrice`; JSON null clears a side | saved configuration |
| GET | `/gold-price/latest` | none | `{price,unit,currency,precision,dataTime}` |

Price values are exact decimal values with up to four fractional digits, nonnegative; both thresholds must satisfy `minPrice < maxPrice`. The configured gold price URL points to the API endpoint, so the desktop app sends its Bearer token. `APP_PRICE_SOURCE_URL` is a server-managed JSON endpoint with `price` as a decimal number or string and `dataTime` as an ISO 8601 timestamp with offset. It must use the same currency and unit as configured. The API uses three-second connection and five-second read timeouts and returns 503 for malformed or unavailable data. The source has no cache in this version.

## Admin endpoints

| Method | Path | Body / filters | Response |
| --- | --- | --- | --- |
| GET | `/admin/users` | `page`, optional `phone`, `status` | paged users sorted by creation descending, then ID |
| PUT | `/admin/users/{id}/password` | `{password}` | `{success:true}` |
| PATCH | `/admin/users/{id}/status` | `{status:0\|1}` | `{id,status}` |
| PATCH | `/admin/users/{id}/validity` | `{validFromDate,validToDate}` | saved dates; null means unbounded |
| GET | `/admin/payments` | `page`, optional `userId`, `paymentNumber`, `createdFrom`, `createdTo`, `paidFrom`, `paidTo` | paged payments sorted creation descending, then ID |
| GET | `/admin/skus` | `page`, optional `name`, `status` | paged SKUs sorted sortOrder, then ID |
| GET | `/admin/skus/{id}` | none | SKU |
| POST | `/admin/skus` | `{name,price,status,sortOrder}` | created SKU, HTTP 201 |
| PUT | `/admin/skus/{id}` | same | updated SKU |
| DELETE | `/admin/skus/{id}` | none | `{success:true}`; 409 if referenced by payment |

Time range filters are half-open `[from,to)` and may be combined. Timestamp inputs and outputs use ISO 8601 with UTC offset; validity dates use `YYYY-MM-DD` and `[validFromDate,validToDate)` semantics. SKU prices are integer cents. User/payment/SKU list fields match the names in responses and database records; user password hashes never appear.
