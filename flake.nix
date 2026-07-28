{
  description = "Development environment for whodat-service";

  inputs = {
    flake-parts.url = "github:hercules-ci/flake-parts";
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
  };

  outputs = inputs @ {flake-parts, ...}:
    flake-parts.lib.mkFlake {inherit inputs;} {
      systems = ["x86_64-linux" "aarch64-linux" "aarch64-darwin" "x86_64-darwin"];
      perSystem = {pkgs, ...}: {
        devShells.default = pkgs.mkShell {
          name = "devenv";

          packages = with pkgs; [
            gradle_9
            kotlin
            kotlin-interactive-shell
            kotlin-language-server
            nixd
            openjdk25_headless
          ];
        };

        formatter = pkgs.alejandra;
      };
    };
}
