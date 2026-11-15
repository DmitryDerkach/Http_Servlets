# 2026: the Java 17 dev container image + one fix.
# The image lists the Yarn apt repository, but its signing key is missing from the image
# (NO_PUBKEY 62D54FD4003F6525), so every "apt-get update" fails - and with it the installation of the
# docker-in-docker feature, which made Codespaces fall back to a recovery container.
# Yarn is not needed here, so its repository is removed before the features are installed.
FROM mcr.microsoft.com/devcontainers/java:1-17-bookworm
RUN rm -f /etc/apt/sources.list.d/yarn.list
