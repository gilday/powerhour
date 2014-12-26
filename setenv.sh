#!/bin/bash
# setenv.sh
#
# sets environment variables necessary to build and sign the PowerHour release
# APK

echo -n 'Keystore: '
read POWERHOUR_KEYSTORE
echo -n 'Alias: '
read POWERHOUR_ALIAS

export POWERHOUR_KEYSTORE=$POWERHOUR_KEYSTORE
export POWERHOUR_ALIAS=$POWERHOUR_ALIAS

