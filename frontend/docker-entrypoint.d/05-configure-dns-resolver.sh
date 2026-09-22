#!/bin/sh

set -eu

resolver_addresses="$(awk '
  $1 == "nameserver" && $2 != "" {
    address = $2;
    if (index(address, ":") > 0 && substr(address, 1, 1) != "[") {
      address = "[" address "]";
    }
    if (addresses != "") {
      addresses = addresses " ";
    }
    addresses = addresses address;
  }
  END { print addresses }
' /etc/resolv.conf)"

if [ -z "$resolver_addresses" ]; then
  echo "Unable to determine a DNS resolver from /etc/resolv.conf" >&2
  exit 1
fi

sed -i "s|__NGINX_DNS_RESOLVER__|${resolver_addresses}|g" \
  /etc/nginx/templates/default.conf.template
