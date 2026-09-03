{ pkgs, ... }: {
  channel = "stable-24_11";
  packages = [ pkgs.jdk17 pkgs.gradle ];
}
