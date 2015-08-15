#! /bin/bash

ps -e | grep ssh$ | perl -ple 's/^\s*//' | cut -f1 -d' ' | xargs kill -2
