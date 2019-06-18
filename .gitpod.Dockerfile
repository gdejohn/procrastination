FROM gitpod/workspace-full

# install and activate Java 11
RUN bash -c ". /home/gitpod/.sdkman/bin/sdkman-init.sh \
             && sdk install java 11.0.2-zulufx \
             && sdk default java 11.0.2-zulufx"
