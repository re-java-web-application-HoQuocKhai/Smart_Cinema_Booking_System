package com.re.smart_cinema_booking_system.controller;

import com.re.smart_cinema_booking_system.dto.MovieRequest;
import com.re.smart_cinema_booking_system.entity.Movie;
import com.re.smart_cinema_booking_system.enums.MovieStatus;
import com.re.smart_cinema_booking_system.exception.BusinessException;
import com.re.smart_cinema_booking_system.service.MovieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/movies")
@RequiredArgsConstructor
public class AdminMovieController {

    private final MovieService movieService;

    @GetMapping
    public String listMovies(@RequestParam(required = false) String keyword, Model model) {
        List<Movie> movies;
        if (keyword != null && !keyword.isBlank()) {
            movies = movieService.searchMovies(keyword.trim());
            model.addAttribute("keyword", keyword);
        } else {
            movies = movieService.getAllMovies();
        }
        model.addAttribute("movies", movies);
        return "admin/movies/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("movieRequest", new MovieRequest());
        model.addAttribute("genres", movieService.getAllGenres());
        model.addAttribute("statuses", MovieStatus.values());
        return "admin/movies/create";
    }

    @PostMapping("/create")
    public String createMovie(@Valid @ModelAttribute MovieRequest movieRequest,
                              BindingResult result,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("genres", movieService.getAllGenres());
            model.addAttribute("statuses", MovieStatus.values());
            return "admin/movies/create";
        }
        try {
            movieService.createMovie(movieRequest);
            redirectAttributes.addFlashAttribute("success", "Thêm phim mới thành công!");
            return "redirect:/admin/movies";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("genres", movieService.getAllGenres());
            model.addAttribute("statuses", MovieStatus.values());
            return "admin/movies/create";
        }
    }

    @GetMapping("/{id}")
    public String movieDetail(@PathVariable Long id, Model model) {
        try {
            Movie movie = movieService.getMovieById(id);
            model.addAttribute("movie", movie);
            return "admin/movies/detail";
        } catch (BusinessException e) {
            return "redirect:/admin/movies";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        try {
            Movie movie = movieService.getMovieById(id);
            MovieRequest movieRequest = movieService.toRequest(movie);
            model.addAttribute("movieRequest", movieRequest);
            model.addAttribute("movieId", id);
            model.addAttribute("genres", movieService.getAllGenres());
            model.addAttribute("statuses", MovieStatus.values());
            return "admin/movies/edit";
        } catch (BusinessException e) {
            return "redirect:/admin/movies";
        }
    }

    @PostMapping("/{id}/edit")
    public String updateMovie(@PathVariable Long id,
                              @Valid @ModelAttribute MovieRequest movieRequest,
                              BindingResult result,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("movieId", id);
            model.addAttribute("genres", movieService.getAllGenres());
            model.addAttribute("statuses", MovieStatus.values());
            return "admin/movies/edit";
        }
        try {
            movieService.updateMovie(id, movieRequest);
            redirectAttributes.addFlashAttribute("success", "Cập nhật phim thành công!");
            return "redirect:/admin/movies";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("movieId", id);
            model.addAttribute("genres", movieService.getAllGenres());
            model.addAttribute("statuses", MovieStatus.values());
            return "admin/movies/edit";
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteMovie(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            movieService.deleteMovie(id);
            redirectAttributes.addFlashAttribute("success", "Xóa phim thành công!");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/movies";
    }
}
